import {ChangeDetectionStrategy, Component, inject, Input, OnDestroy} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {TranslatePipe} from '@ngx-translate/core';
import {catchError, forkJoin, of, Subscription} from 'rxjs';
import {LocalDatePipe} from '../../libs/pipes/local-date.pipe';
import {ComponentDto} from '../../repositories/dto/component.dto';
import {ComponentEnvironmentDto, ComponentEnvironmentRequest} from '../../repositories/dto/component-environment.dto';
import {EnvironmentDto} from '../../repositories/dto/environment.dto';
import {EolStatusDto} from '../../repositories/dto/eol-status.dto';
import {UpdateCheckDto} from '../../repositories/dto/update-check.dto';
import {UpgradeProposalDto} from '../../repositories/dto/upgrade.dto';
import {DependencyCheckDto} from '../../repositories/dto/dependency.dto';
import {VersionChangeDto} from '../../repositories/dto/version-change.dto';
import {UpdateDecisionDto} from '../../repositories/dto/update-decision.dto';
import {ComponentChangeDto} from '../../repositories/dto/component-change.dto';
import {ComponentRepo} from '../../repositories/api/component.repo';
import {ComponentEnvironmentRepo} from '../../repositories/api/component-environment.repo';
import {EnvironmentRepo} from '../../repositories/api/environment.repo';
import {EolStatusRepo} from '../../repositories/api/eol-status.repo';
import {UpdateCheckRepo} from '../../repositories/api/update-check.repo';
import {VersionChangeRepo} from '../../repositories/api/version-change.repo';
import {TaskRepo} from '../../repositories/api/task.repo';
import {VersionChangeDialogComponent} from '../../components/version-change-dialog/version-change-dialog.component';
import {DecisionDialogComponent} from '../../components/decision-dialog/decision-dialog.component';
import {PlanDialogComponent} from '../../components/plan-dialog/plan-dialog.component';
import {CopyValueComponent} from '../../components/copy-value/copy-value.component';
import {VersionComponent} from '../../components/version/version.component';
import {ProjectSelectionService} from '../../services/project-selection/project-selection.service';
import {isDefined, nonBlank, unsubscribe} from '../../libs/utils/vc.utils';

/** One environment of this component, with whatever is known about it. */
export class EnvironmentRow {

  public environment!: EnvironmentDto;
  public cell?: ComponentEnvironmentDto;

  constructor(init?: Partial<EnvironmentRow>) {
    Object.assign(this, init);
  }
}

/**
 * Everything about one component: what it is, where it runs, and every change
 * that was ever recorded for it — newest first, because that is the end people
 * read.
 */
@Component({
  // Default, explicitly: this Angular version compiles a component as OnPush
  // unless told otherwise, and these components hold their state in plain
  // fields written from subscribe callbacks. Under OnPush such a view is
  // never marked dirty, so every page renders its heading and then stays
  // empty for good - with no error anywhere. Until the state is held in
  // signals, the strategy has to say what the code assumes.
  changeDetection: ChangeDetectionStrategy.Default,
  selector: 'vc-component-detail',
  imports: [CopyValueComponent, LocalDatePipe, RouterLink, TranslatePipe, VersionComponent],
  templateUrl: './component-detail.component.html'
})
export class ComponentDetailComponent implements OnDestroy {

  private readonly componentRepo: ComponentRepo = inject(ComponentRepo);
  private readonly environmentRepo: EnvironmentRepo = inject(EnvironmentRepo);
  private readonly componentEnvironmentRepo: ComponentEnvironmentRepo = inject(ComponentEnvironmentRepo);
  private readonly versionChangeRepo: VersionChangeRepo = inject(VersionChangeRepo);
  private readonly updateCheckRepo: UpdateCheckRepo = inject(UpdateCheckRepo);
  private readonly eolStatusRepo: EolStatusRepo = inject(EolStatusRepo);
  private readonly taskRepo: TaskRepo = inject(TaskRepo);
  private readonly projectSelection: ProjectSelectionService = inject(ProjectSelectionService);
  private readonly modal: NgbModal = inject(NgbModal);
  private readonly subs: Subscription[] = [];

  private componentId?: string;

  /** Placeholder for an empty field; punctuation, so not a translation key. */
  protected readonly dash = '–';

  protected component?: ComponentDto;
  protected rows: EnvironmentRow[] = [];
  protected history: VersionChangeDto[] = [];
  protected check?: UpdateCheckDto;
  protected eol?: EolStatusDto;
  protected proposals: UpgradeProposalDto[] = [];
  /** What this component needs. */
  protected requires: DependencyCheckDto[] = [];
  /** What needs this component — the direction that blocks an update. */
  protected requiredBy: DependencyCheckDto[] = [];
  /** What was decided about updates; a hold is shown with its reason next to the name. */
  protected decision?: UpdateDecisionDto;
  /** Master data, decisions and rollout plans, newest first. */
  protected changes: ComponentChangeDto[] = [];
  protected checking = false;
  protected checkingEol = false;
  protected loading = true;
  protected errorMessage?: string;

  /**
   * From the route, bound by `withComponentInputBinding`. A setter rather than
   * `ngOnInit`: the router reuses the instance when only the id changes, and the
   * page would otherwise keep showing the component that was open before.
   */
  @Input()
  public set id(value: string) {
    if (!nonBlank(value) || value === this.componentId) {
      return;
    }
    this.componentId = value;
    this.load();
  }

  public ngOnDestroy(): void {
    unsubscribe(this.subs);
  }

  /** The environment of a check, by id — the entries are per environment. */
  protected checkEnvironmentName(check: DependencyCheckDto): string {
    return this.environmentName(check.environmentId);
  }

  protected environmentName(environmentId: string): string {
    return this.rows.find((row) => row.environment.id === environmentId)?.environment.name ?? '';
  }

  protected held(): boolean {
    return this.decision?.state === 'HELD';
  }

  protected autoUpdating(): boolean {
    return this.decision?.state === 'AUTO_UPDATE';
  }

  /**
   * Also here, not only on the task list: a component that is not behind is not on that list, and holding it
   * is exactly the decision taken before it would be.
   */
  protected openDecision(): void {
    if (!isDefined(this.component)) {
      return;
    }
    const reference = this.modal.open(DecisionDialogComponent);
    reference.componentInstance.componentId = this.component.id;
    reference.componentInstance.componentName = this.component.name;
    reference.componentInstance.latestVersion = this.check?.latestVersion;
    reference.result.then(() => this.load(), () => undefined);
  }

  protected openPlan(row: EnvironmentRow): void {
    if (!isDefined(this.component) || !isDefined(row.cell) || !row.cell.active) {
      return;
    }
    const reference = this.modal.open(PlanDialogComponent);
    reference.componentInstance.cell = row.cell;
    reference.componentInstance.componentName = this.component.name;
    reference.componentInstance.environmentName = row.environment.name;
    reference.componentInstance.suggestedVersion =
      this.proposals.find((proposal) => proposal.environmentId === row.environment.id)?.target;
    reference.result.then(() => this.load(), () => undefined);
  }

  protected unplan(row: EnvironmentRow): void {
    if (!isDefined(row.cell)) {
      return;
    }
    this.subs.push(this.componentEnvironmentRepo.unplan(row.cell.id).subscribe({
      next: () => this.load(),
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  protected openVersionChange(row: EnvironmentRow): void {
    if (!isDefined(this.component) || !isDefined(row.cell) || !row.cell.active) {
      return;
    }
    const reference = this.modal.open(VersionChangeDialogComponent, {size: 'lg'});
    reference.componentInstance.component = this.component;
    reference.componentInstance.cell = row.cell;
    reference.componentInstance.environmentName = row.environment.name;
    reference.result.then(() => this.load(), () => undefined);
  }

  /**
   * Switching a cell on or off says whether the component belongs in that
   * environment at all. It never touches the version — that is the dialog.
   */
  protected setActive(row: EnvironmentRow, active: boolean): void {
    if (!isDefined(this.component)) {
      return;
    }
    const request = new ComponentEnvironmentRequest({
      componentId: this.component.id,
      environmentId: row.environment.id,
      active: active
    });
    this.subs.push(this.componentEnvironmentRepo.createOrUpdate(request).subscribe({
      next: () => this.load(),
      // The refusal is a translated sentence; swallowing it leaves a button that
      // looks broken instead of one that says why it did nothing.
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  /**
   * Asks the source now. The server applies the same minimum interval as the
   * scheduler, so a refusal is an answer and gets shown like any other.
   */
  protected checkNow(): void {
    if (!isDefined(this.component) || this.checking) {
      return;
    }
    this.checking = true;
    this.errorMessage = undefined;
    this.subs.push(this.updateCheckRepo.checkNow(this.component.id).subscribe({
      next: (check) => {
        this.check = check;
        this.checking = false;
      },
      error: (error: HttpErrorResponse) => {
        this.checking = false;
        this.showError(error);
      }
    }));
  }

  /**
   * Asks the end-of-life database now. Its own cycle and its own bound: the
   * server refuses a second lookup inside the weekly interval.
   */
  protected checkEolNow(): void {
    if (!isDefined(this.component) || this.checkingEol) {
      return;
    }
    this.checkingEol = true;
    this.errorMessage = undefined;
    this.subs.push(this.eolStatusRepo.checkNow(this.component.id).subscribe({
      next: (statuses) => {
        this.eol = statuses.at(0);
        this.checkingEol = false;
      },
      error: (error: HttpErrorResponse) => {
        this.checkingEol = false;
        this.showError(error);
      }
    }));
  }

  private load(): void {
    if (!isDefined(this.componentId)) {
      return;
    }
    this.loading = true;
    this.errorMessage = undefined;
    this.subs.push(this.componentRepo.getById(this.componentId).subscribe({
      next: (component) => this.loadDetails(component),
      error: (error: HttpErrorResponse) => this.failed(error)
    }));
  }

  private loadDetails(component: ComponentDto): void {
    this.component = component;
    // The way back to the matrix leads into this component's project, after a deep link too.
    this.projectSelection.select(component.projectId);
    this.subs.push(forkJoin({
      environments: this.environmentRepo.findAllByProject(component.projectId),
      cells: this.componentEnvironmentRepo.findAllByComponent(component.id),
      history: this.versionChangeRepo.findAllByComponent(component.id),
      // Caught here, not by the shared handler: the check block is extra
      // information, and losing it must not take the name, the environments and
      // the whole history down with it.
      checks: this.updateCheckRepo.findAllByComponent(component.id).pipe(catchError(() => of([]))),
      eol: this.eolStatusRepo.findAllByComponent(component.id).pipe(catchError(() => of([]))),
      proposals: this.componentRepo.getProposals(component.id).pipe(catchError(() => of([]))),
      requires: this.componentRepo.getDependencies(component.id, false).pipe(catchError(() => of([]))),
      requiredBy: this.componentRepo.getDependencies(component.id, true).pipe(catchError(() => of([]))),
      decisions: this.taskRepo.findDecisionsByComponent(component.id).pipe(catchError(() => of([]))),
      changes: this.componentRepo.getChanges(component.id).pipe(catchError(() => of([])))
    }).subscribe({
      next: (loaded) => {
        // Never checked is an empty list, not an error: it is the state every
        // component starts in.
        this.check = loaded.checks.at(0);
        this.eol = loaded.eol.at(0);
        this.proposals = loaded.proposals;
        this.requires = loaded.requires;
        this.requiredBy = loaded.requiredBy;
        this.decision = loaded.decisions.at(0);
        this.changes = loaded.changes;
        this.rows = loaded.environments.map((environment) => new EnvironmentRow({
          environment: environment,
          cell: loaded.cells.find((cell) => cell.environmentId === environment.id)
        }));
        this.history = loaded.history;
        this.loading = false;
      },
      error: (error: HttpErrorResponse) => this.failed(error)
    }));
  }

  /**
   * A failure has to end the loading state as well. Left standing it hides both
   * the error and the "no such component" text behind a page that stays blank.
   */
  private failed(error: HttpErrorResponse): void {
    this.component = undefined;
    this.rows = [];
    this.history = [];
    this.check = undefined;
    this.eol = undefined;
    this.proposals = [];
    this.requires = [];
    this.requiredBy = [];
    this.decision = undefined;
    this.changes = [];
    this.loading = false;
    this.showError(error);
  }

  private showError(error: HttpErrorResponse): void {
    this.errorMessage = error.error?.message ?? error.message;
  }
}
