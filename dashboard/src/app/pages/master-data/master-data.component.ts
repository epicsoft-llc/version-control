import {ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {catchError, forkJoin, Observable, of, Subject, Subscription, switchMap, tap} from 'rxjs';
import {ProjectDto, ProjectRequest} from '../../repositories/dto/project.dto';
import {EnvironmentDto, EnvironmentRequest} from '../../repositories/dto/environment.dto';
import {ComponentDto, ComponentPackageRequest, ComponentRequest} from '../../repositories/dto/component.dto';
import {MAX_STEPS, SOURCE_TYPES, VERSION_STRATEGIES} from '../../repositories/dto/enums.dto';
import {VersionPreviewDto, VersionPreviewRequest} from '../../repositories/dto/update-check.dto';
import {UpgradeStopRequest} from '../../repositories/dto/upgrade.dto';
import {ComponentDependencyRequest} from '../../repositories/dto/dependency.dto';
import {ProjectRepo} from '../../repositories/api/project.repo';
import {EnvironmentRepo} from '../../repositories/api/environment.repo';
import {ComponentRepo} from '../../repositories/api/component.repo';
import {UpdateCheckRepo} from '../../repositories/api/update-check.repo';
import {ProjectSelectionService} from '../../services/project-selection/project-selection.service';
import {isDefined, nonBlank, notDefined, unsubscribe} from '../../libs/utils/vc.utils';

/** One row of the dependency editor: what is needed, in which range, and why. */
export class DependencyRow {

  public dependsOnId = '';
  public versionRange = '';
  public note = '';

  constructor(init?: Partial<DependencyRow>) {
    Object.assign(this, init);
  }
}

/** One row of the stop editor: a version that cannot be skipped, and why. */
export class StopRow {

  public version = '';
  public note = '';

  constructor(init?: Partial<StopRow>) {
    Object.assign(this, init);
  }
}

/** One row of the package editor: a name, an order and the leading flag. */
export class PackageRow {

  public name = '';
  public leadingPackage = false;

  constructor(init?: Partial<PackageRow>) {
    Object.assign(this, init);
  }
}

/**
 * Master data: projects, their environments and their components.
 *
 * <p>Deliberately plain. It is opened when something is set up or renamed, which
 * is a handful of times a year — every minute spent making it clever is a minute
 * not spent on the views that are used daily.
 */
@Component({
  // Default, explicitly: this Angular version compiles a component as OnPush
  // unless told otherwise, and these components hold their state in plain
  // fields written from subscribe callbacks. Under OnPush such a view is
  // never marked dirty, so every page renders its heading and then stays
  // empty for good - with no error anywhere. Until the state is held in
  // signals, the strategy has to say what the code assumes.
  changeDetection: ChangeDetectionStrategy.Default,
  selector: 'vc-master-data',
  imports: [FormsModule, RouterLink, TranslatePipe],
  templateUrl: './master-data.component.html'
})
export class MasterDataComponent implements OnInit, OnDestroy {

  private readonly projectRepo: ProjectRepo = inject(ProjectRepo);
  private readonly environmentRepo: EnvironmentRepo = inject(EnvironmentRepo);
  private readonly componentRepo: ComponentRepo = inject(ComponentRepo);
  private readonly updateCheckRepo: UpdateCheckRepo = inject(UpdateCheckRepo);
  private readonly projectSelection: ProjectSelectionService = inject(ProjectSelectionService);
  private readonly translate: TranslateService = inject(TranslateService);
  private readonly subs: Subscription[] = [];

  /**
   * Every project switch goes through here. Two clicks in a row otherwise leave
   * the slower answer on screen, and the delete buttons then act on a project
   * the reader is no longer looking at.
   */
  private readonly selection = new Subject<string>();

  protected readonly sourceTypes = SOURCE_TYPES;
  protected readonly versionStrategies = VERSION_STRATEGIES;
  protected readonly maxSteps = MAX_STEPS;

  protected projects: ProjectDto[] = [];
  protected environments: EnvironmentDto[] = [];
  protected components: ComponentDto[] = [];
  protected selectedProjectId?: string;
  protected errorMessage?: string;

  protected projectForm?: ProjectRequest;
  protected projectFormId?: string;
  protected environmentForm?: EnvironmentRequest;
  protected environmentFormId?: string;
  protected componentForm?: ComponentRequest;
  /** The placeholder of the release notes field: a URL with its {version}, which is no text to translate. */
  protected readonly releaseNotesExample = 'https://github.com/traefik/traefik/releases/tag/v{version}';
  protected componentFormId?: string;
  protected packageRows: PackageRow[] = [];
  protected stopRows: StopRow[] = [];
  protected dependencyRows: DependencyRow[] = [];
  protected prerequisites: ComponentDto[] = [];
  protected preview?: VersionPreviewDto;
  protected previewing = false;

  public ngOnInit(): void {
    this.subs.push(this.selection.pipe(
      tap((projectId) => {
        this.selectedProjectId = projectId;
        // Cleared before the request: the previous project's environments and
        // components under this project's heading are worse than an empty table.
        this.environments = [];
        this.components = [];
        this.closeForms();
      }),
      switchMap((projectId) => forkJoin({
        environments: this.environmentRepo.findAllByProject(projectId),
        components: this.componentRepo.findAllByProject(projectId)
      }).pipe(catchError((error) => {
        this.showError(error);
        return of(undefined);
      })))
    ).subscribe((loaded) => {
      this.environments = loaded?.environments ?? [];
      this.components = loaded?.components ?? [];
    }));

    this.loadProjects();
  }

  public ngOnDestroy(): void {
    unsubscribe(this.subs);
  }

  protected selectProject(projectId: string): void {
    this.projectSelection.select(projectId);
    this.selection.next(projectId);
  }

  // #### Projects
  protected newProject(): void {
    this.closeForms();
    this.projectForm = new ProjectRequest({name: ''});
  }

  protected editProject(project: ProjectDto): void {
    this.closeForms();
    this.projectFormId = project.id;
    this.projectForm = new ProjectRequest({
      name: project.name,
      description: project.description,
      sortOrder: project.sortOrder
    });
  }

  protected saveProject(): void {
    if (notDefined(this.projectForm) || !nonBlank(this.projectForm.name)) {
      return;
    }
    const request = this.projectForm;
    const call: Observable<ProjectDto> = isDefined(this.projectFormId)
                                         ? this.projectRepo.update(this.projectFormId, request)
                                         : this.projectRepo.create(request);
    this.subs.push(call.subscribe({
      next: (project) => {
        this.closeForms();
        this.loadProjects(project.id);
      },
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  protected deleteProject(project: ProjectDto): void {
    this.subs.push(this.projectRepo.delete(project.id).subscribe({
      next: () => {
        // Only when the reader was in it; deleting another one must not move them.
        if (project.id === this.selectedProjectId) {
          this.selectedProjectId = undefined;
        }
        this.loadProjects();
      },
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  // #### Environments
  protected newEnvironment(): void {
    if (notDefined(this.selectedProjectId)) {
      return;
    }
    this.closeForms();
    this.environmentForm = new EnvironmentRequest({projectId: this.selectedProjectId, name: '', production: false});
  }

  protected editEnvironment(environment: EnvironmentDto): void {
    this.closeForms();
    this.environmentFormId = environment.id;
    this.environmentForm = new EnvironmentRequest({
      projectId: environment.projectId,
      name: environment.name,
      sortOrder: environment.sortOrder,
      production: environment.production
    });
  }

  protected saveEnvironment(): void {
    if (notDefined(this.environmentForm) || !nonBlank(this.environmentForm.name)) {
      return;
    }
    const request = this.environmentForm;
    const call: Observable<EnvironmentDto> = isDefined(this.environmentFormId)
                                             ? this.environmentRepo.update(this.environmentFormId, request)
                                             : this.environmentRepo.create(request);
    this.subs.push(call.subscribe({
      next: () => this.reloadProjectData(),
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  protected deleteEnvironment(environment: EnvironmentDto): void {
    this.subs.push(this.environmentRepo.delete(environment.id).subscribe({
      next: () => this.reloadProjectData(),
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  // #### Components
  protected newComponent(): void {
    if (notDefined(this.selectedProjectId)) {
      return;
    }
    this.closeForms();
    this.componentForm = new ComponentRequest({
      projectId: this.selectedProjectId,
      name: '',
      sourceType: 'DOCKER_HUB',
      versionStrategy: 'SEMVER',
      maxStep: 'MINOR',
      checkEnabled: true
    });
    this.packageRows = [];
    this.stopRows = [];
    this.dependencyRows = [];
    this.updatePrerequisites();
  }

  protected editComponent(component: ComponentDto): void {
    this.closeForms();
    this.componentFormId = component.id;
    this.componentForm = new ComponentRequest({
      projectId: component.projectId,
      name: component.name,
      description: component.description,
      sourceType: component.sourceType,
      sourceRef: component.sourceRef,
      aptSuite: component.aptSuite,
      aptComponent: component.aptComponent,
      aptArchitecture: component.aptArchitecture,
      tagPattern: component.tagPattern,
      versionStrategy: component.versionStrategy,
      majorConstraint: component.majorConstraint,
      allowPrerelease: component.allowPrerelease,
      maxStep: component.maxStep,
      upgradePathNotes: component.upgradePathNotes,
      upgradeDocUrl: component.upgradeDocUrl,
      releaseNotesUrl: component.releaseNotesUrl,
      needsMaintenanceWindow: component.needsMaintenanceWindow,
      eolProduct: component.eolProduct,
      eolCycle: component.eolCycle,
      checkEnabled: component.checkEnabled
    });
    this.packageRows = component.packages.map((entry) => new PackageRow({
      name: entry.name,
      leadingPackage: entry.leadingPackage
    }));
    this.stopRows = component.upgradeStops.map((entry) => new StopRow({
      version: entry.version,
      note: entry.note ?? ''
    }));
    this.dependencyRows = component.dependencies.map((entry) => new DependencyRow({
      dependsOnId: entry.dependsOnId,
      versionRange: entry.versionRange,
      note: entry.note ?? ''
    }));
    this.updatePrerequisites();
  }

  protected saveComponent(): void {
    if (notDefined(this.componentForm) || !nonBlank(this.componentForm.name)) {
      return;
    }
    const request = this.componentForm;
    // Always sent, so removing the last package actually removes it — an omitted
    // list would leave it standing. An empty list for a component that no longer
    // comes from a package repository is the point: packages left behind would
    // make the version dialog demand one version per package for a plain image.
    request.packages = this.usesPackages()
                       ? this.packageRows
                             .filter((row) => nonBlank(row.name))
                             .map((row, index) => new ComponentPackageRequest({
                               name: row.name.trim(),
                               sortOrder: index,
                               leadingPackage: row.leadingPackage
                             }))
                       : [];
    // Always sent, so removing the last stop actually removes it.
    request.upgradeStops = this.stopRows
                               .filter((row) => nonBlank(row.version))
                               .map((row) => new UpgradeStopRequest({
                                 version: row.version.trim(),
                                 note: nonBlank(row.note) ? row.note.trim() : undefined
                               }));
    // Half filled rows are refused rather than dropped: a row that disappears on
    // save looks exactly like one that was stored, and the reader finds out
    // weeks later that the dependency was never there.
    if (this.dependencyRows.some((row) => nonBlank(row.dependsOnId) !== nonBlank(row.versionRange))) {
      this.errorMessage = this.translate.instant('masterData.dependencies.incomplete');
      return;
    }
    // Always sent, so removing the last dependency actually removes it.
    request.dependencies = this.dependencyRows
                               .filter((row) => nonBlank(row.dependsOnId) && nonBlank(row.versionRange))
                               .map((row) => new ComponentDependencyRequest({
                                 dependsOnId: row.dependsOnId,
                                 versionRange: row.versionRange.trim(),
                                 note: nonBlank(row.note) ? row.note.trim() : undefined
                               }));
    const call: Observable<ComponentDto> = isDefined(this.componentFormId)
                                           ? this.componentRepo.update(this.componentFormId, request)
                                           : this.componentRepo.create(request);
    this.subs.push(call.subscribe({
      next: () => this.reloadProjectData(),
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  protected deleteComponent(component: ComponentDto): void {
    this.subs.push(this.componentRepo.delete(component.id).subscribe({
      next: () => this.reloadProjectData(),
      error: (error: HttpErrorResponse) => this.showError(error)
    }));
  }

  /**
   * Shows which tags the rule in the form would accept and which of them wins.
   *
   * <p>Not a convenience. Without it a regular expression is written against a
   * tag list nobody can see, and that is where rule maintenance fails in
   * practice. The server answers a pattern that matches nothing with an
   * explanation rather than an error, because half finished patterns are the
   * normal state of this field.
   */
  protected previewRule(): void {
    if (notDefined(this.componentFormId) || this.previewing) {
      // Only for a component that exists: the preview asks its configured source.
      return;
    }
    this.previewing = true;
    this.preview = undefined;
    // The whole rule as it stands in the form, not only the pattern: a preview
    // that answered with the saved major constraint while the form shows another
    // one would answer a question nobody asked.
    const request = new VersionPreviewRequest({
      componentId: this.componentFormId,
      tagPattern: this.componentForm?.tagPattern,
      majorConstraint: this.componentForm?.majorConstraint,
      allowPrerelease: this.componentForm?.allowPrerelease,
      versionStrategy: this.componentForm?.versionStrategy
    });
    this.subs.push(this.updateCheckRepo.preview(request).subscribe({
      next: (preview) => {
        this.preview = preview;
        this.previewing = false;
      },
      error: (error: HttpErrorResponse) => {
        this.previewing = false;
        this.showError(error);
      }
    }));
  }

  protected addDependencyRow(): void {
    this.dependencyRows.push(new DependencyRow());
  }

  protected removeDependencyRow(index: number): void {
    this.dependencyRows.splice(index, 1);
  }

  /**
   * Everything in the project except the component being edited, worked out when
   * the form opens rather than on every change detection pass. While a new one is
   * being created there is nothing to exclude, and the backend takes the
   * dependencies along with the create — so the editor works there too.
   */
  private updatePrerequisites(): void {
    this.prerequisites = this.components.filter((entry) => entry.id !== this.componentFormId);
  }

  protected addStopRow(): void {
    this.stopRows.push(new StopRow());
  }

  protected removeStopRow(index: number): void {
    this.stopRows.splice(index, 1);
  }

  protected addPackageRow(): void {
    this.packageRows.push(new PackageRow({leadingPackage: this.packageRows.length === 0}));
  }

  protected removePackageRow(index: number): void {
    this.packageRows.splice(index, 1);
    // Removing the row that was leading would leave the component without one,
    // and the matrix cell of a package component would then stay empty however
    // many versions are recorded.
    if (this.packageRows.length > 0 && !this.packageRows.some((row) => row.leadingPackage)) {
      this.packageRows[0].leadingPackage = true;
    }
  }

  /** Exactly one package leads: it is the version the matrix cell shows. */
  protected setLeadingPackage(index: number): void {
    this.packageRows.forEach((row, position) => row.leadingPackage = position === index);
  }

  protected usesPackages(): boolean {
    return this.componentForm?.sourceType === 'APT_REPOSITORY';
  }

  protected closeForms(): void {
    this.projectForm = undefined;
    this.projectFormId = undefined;
    this.environmentForm = undefined;
    this.environmentFormId = undefined;
    this.componentForm = undefined;
    this.componentFormId = undefined;
    this.packageRows = [];
    this.stopRows = [];
    this.dependencyRows = [];
    this.preview = undefined;
    this.errorMessage = undefined;
  }

  private reloadProjectData(): void {
    if (isDefined(this.selectedProjectId)) {
      this.selectProject(this.selectedProjectId);
    }
  }

  private loadProjects(selectId?: string): void {
    this.subs.push(this.projectRepo.findAll().subscribe((projects) => {
      this.projects = projects;
      const target = selectId ?? this.selectedProjectId ?? this.projectSelection.pick(projects);
      if (isDefined(target)) {
        this.selectProject(target);
      }
    }));
  }

  private showError(error: HttpErrorResponse): void {
    // The server sends a translated sentence; the status code alone would not
    // say which name is already taken.
    this.errorMessage = error.error?.message ?? error.message;
  }
}
