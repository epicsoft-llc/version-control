import {ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {TranslatePipe} from '@ngx-translate/core';
import {catchError, forkJoin, of, Subject, Subscription, switchMap, tap} from 'rxjs';
import {LocalDatePipe} from '../../libs/pipes/local-date.pipe';
import {ProjectDto} from '../../repositories/dto/project.dto';
import {MatrixCellDto, MatrixDto, MatrixRowDto} from '../../repositories/dto/matrix.dto';
import {UpdateCheckDto} from '../../repositories/dto/update-check.dto';
import {UpdateDecisionDto} from '../../repositories/dto/update-decision.dto';
import {ProjectRepo} from '../../repositories/api/project.repo';
import {TaskRepo} from '../../repositories/api/task.repo';
import {ComponentRepo} from '../../repositories/api/component.repo';
import {ComponentEnvironmentRepo} from '../../repositories/api/component-environment.repo';
import {UpdateCheckRepo} from '../../repositories/api/update-check.repo';
import {VersionChangeDialogComponent} from '../../components/version-change-dialog/version-change-dialog.component';
import {CopyValueComponent} from '../../components/copy-value/copy-value.component';
import {ProjectSelectionService} from '../../services/project-selection/project-selection.service';
import {isDefined, notDefined, unsubscribe} from '../../libs/utils/vc.utils';

/**
 * The overview: components as rows, environments as columns.
 *
 * <p>The four cell states are drawn differently on purpose. A cell that is
 * switched off says "not here, and that is intended"; an empty one says "runs
 * here and nobody knows which version" — reading one as the other is the mistake
 * this table exists to prevent.
 */
@Component({
  // Default, explicitly: this Angular version compiles a component as OnPush
  // unless told otherwise, and these components hold their state in plain
  // fields written from subscribe callbacks. Under OnPush such a view is
  // never marked dirty, so every page renders its heading and then stays
  // empty for good - with no error anywhere. Until the state is held in
  // signals, the strategy has to say what the code assumes.
  changeDetection: ChangeDetectionStrategy.Default,
  selector: 'vc-matrix',
  imports: [CopyValueComponent, FormsModule, LocalDatePipe, RouterLink, TranslatePipe],
  templateUrl: './matrix.component.html'
})
export class MatrixComponent implements OnInit, OnDestroy {

  private readonly projectRepo: ProjectRepo = inject(ProjectRepo);
  private readonly componentRepo: ComponentRepo = inject(ComponentRepo);
  private readonly componentEnvironmentRepo: ComponentEnvironmentRepo = inject(ComponentEnvironmentRepo);
  private readonly updateCheckRepo: UpdateCheckRepo = inject(UpdateCheckRepo);
  private readonly taskRepo: TaskRepo = inject(TaskRepo);
  private readonly projectSelection: ProjectSelectionService = inject(ProjectSelectionService);
  private readonly modal: NgbModal = inject(NgbModal);
  private readonly subs: Subscription[] = [];

  /**
   * Every selection goes through here so that a slow answer for the project the
   * reader has already left cannot overwrite the one they are looking at.
   */
  private readonly selection = new Subject<string>();

  protected projects: ProjectDto[] = [];
  protected selectedProjectId?: string;
  protected matrix?: MatrixDto;
  /** By component id, so a row can show what the last check found. */
  protected checks = new Map<string, UpdateCheckDto>();
  /** Held components by id: they stay on their version on purpose, so the row must not call for an update. */
  protected holds = new Map<string, UpdateDecisionDto>();
  protected loading = true;
  protected errorMessage?: string;

  public ngOnInit(): void {
    this.subs.push(this.selection.pipe(
      tap((projectId) => {
        this.selectedProjectId = projectId;
        // Dropped before the request, not after it: the previous project's rows
        // under the new project's name would be read as this project's state.
        this.matrix = undefined;
        this.loading = true;
        this.errorMessage = undefined;
      }),
      switchMap((projectId) => forkJoin({
        matrix: this.projectRepo.getMatrix(projectId),
        // The available column is auxiliary. A failure there must not take the
        // matrix with it — that is the screen people came for.
        checks: this.updateCheckRepo.findAllByProject(projectId).pipe(catchError(() => of([]))),
        decisions: this.taskRepo.findDecisionsByProject(projectId).pipe(catchError(() => of([])))
      }).pipe(catchError((error) => {
        this.showError(error);
        return of(undefined);
      })))
    ).subscribe((loaded) => {
      this.matrix = loaded?.matrix;
      this.checks = new Map((loaded?.checks ?? []).map((check) => [check.componentId, check]));
      this.holds = new Map((loaded?.decisions ?? []).filter((decision) => decision.state === 'HELD')
                                                     .map((decision) => [decision.componentId, decision]));
      this.loading = false;
    }));

    this.subs.push(this.projectRepo.findAll().subscribe({
      next: (projects) => {
        this.projects = projects;
        this.loading = false;
        const target = this.projectSelection.pick(projects);
        if (isDefined(target)) {
          this.selectProject(target);
        }
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.showError(error);
      }
    }));
  }

  public ngOnDestroy(): void {
    unsubscribe(this.subs);
  }

  protected selectProject(projectId: string): void {
    this.projectSelection.select(projectId);
    this.selection.next(projectId);
  }

  protected checkOf(componentId: string): UpdateCheckDto | undefined {
    return this.checks.get(componentId);
  }

  protected heldOf(componentId: string): UpdateDecisionDto | undefined {
    return this.holds.get(componentId);
  }

  /**
   * True when the source offers something the row does not run anywhere. Read
   * from the recorded versions rather than from a comparison of version numbers:
   * how far apart two versions are belongs to the step rules, which come later.
   */
  protected updateAvailable(row: MatrixRowDto): boolean {
    const latest = this.checkOf(row.componentId)?.latestVersion;
    if (notDefined(latest) || isDefined(this.heldOf(row.componentId))) {
      return false;
    }
    // Any environment that is behind counts, not only the case where every one
    // of them is. Half way through a rollout production is still on the old
    // version, and that is exactly when the row has to keep saying so.
    return row.cells.some((cell) => cell.state === 'RECORDED' && cell.currentVersion !== latest);
  }

  protected environmentName(environmentId: string): string {
    return this.matrix?.environments.find((environment) => environment.id === environmentId)?.name ?? '';
  }

  /** Only a cell that exists and is switched on can take a new version. */
  protected canChange(cell: MatrixCellDto): boolean {
    return isDefined(cell.componentEnvironmentId) && cell.state !== 'INACTIVE';
  }

  protected openVersionChange(componentId: string, cell: MatrixCellDto): void {
    if (!this.canChange(cell) || !isDefined(cell.componentEnvironmentId)) {
      return;
    }
    const cellId = cell.componentEnvironmentId;
    this.subs.push(forkJoin({
      component: this.componentRepo.getById(componentId),
      cell: this.componentEnvironmentRepo.getById(cellId)
    }).subscribe({
      next: (loaded) => {
        const reference = this.modal.open(VersionChangeDialogComponent, {size: 'lg'});
        reference.componentInstance.component = loaded.component;
        reference.componentInstance.cell = loaded.cell;
        reference.componentInstance.environmentName = this.environmentName(cell.environmentId);
        reference.result.then(() => this.reload(), () => undefined);
      },
      // Without this the click does nothing at all and nothing says why.
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  private reload(): void {
    if (isDefined(this.selectedProjectId)) {
      this.selectProject(this.selectedProjectId);
    }
  }

  private showError(error: HttpErrorResponse): void {
    // The server sends a translated sentence; the status code alone would leave
    // the reader looking at an empty page with no idea what happened.
    this.errorMessage = error.error?.message ?? error.message;
  }
}
