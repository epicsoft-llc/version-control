import {provideZoneChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentEnvironmentRepo} from '../../repositories/api/component-environment.repo';
import {ComponentRepo} from '../../repositories/api/component.repo';
import {EnvironmentRepo} from '../../repositories/api/environment.repo';
import {EolStatusRepo} from '../../repositories/api/eol-status.repo';
import {UpdateCheckRepo} from '../../repositories/api/update-check.repo';
import {VersionChangeRepo} from '../../repositories/api/version-change.repo';
import {TaskRepo} from '../../repositories/api/task.repo';
import {ComponentChangeDto} from '../../repositories/dto/component-change.dto';
import {ComponentDto} from '../../repositories/dto/component.dto';
import {ComponentEnvironmentDto} from '../../repositories/dto/component-environment.dto';
import {DependencyCheckDto} from '../../repositories/dto/dependency.dto';
import {EnvironmentDto} from '../../repositories/dto/environment.dto';
import {UpdateCheckDto} from '../../repositories/dto/update-check.dto';
import {UpdateDecisionDto} from '../../repositories/dto/update-decision.dto';
import {ReleaseLinkDto, UpgradeProposalDto} from '../../repositories/dto/upgrade.dto';
import {ComponentDetailComponent} from './component-detail.component';

/**
 * One component, everything that is known about it. The page pulls eight
 * resources together, and what matters is what happens when one of them is
 * missing or fails: the name, the environments and the history are what people
 * came for, and a failing extra block must not take them along.
 */
describe('ComponentDetailComponent', () => {

  let fixture: ComponentFixture<ComponentDetailComponent>;
  let repos: Record<string, ReturnType<typeof vi.fn>>;

  const production = new EnvironmentDto({id: 'environment-1', name: 'Production', production: true});
  const staging = new EnvironmentDto({id: 'environment-2', name: 'Staging', production: false});

  const component = new ComponentDto({id: 'component-1', projectId: 'project-1', name: 'PostgreSQL',
                                      sourceType: 'DOCKER_HUB', sourceRef: 'library/postgres', packages: []});

  const text = (): string => (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(ComponentDetailComponent);
    fixture.componentInstance.id = 'component-1';
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    repos = {
      getById: vi.fn().mockReturnValue(of(component)),
      findEnvironments: vi.fn().mockReturnValue(of([staging, production])),
      findCells: vi.fn().mockReturnValue(of([
        new ComponentEnvironmentDto({id: 'cell-1', componentId: 'component-1', environmentId: 'environment-1',
                                     currentVersion: '17.2', active: true, movingTag: false, packages: []})
      ])),
      findHistory: vi.fn().mockReturnValue(of([])),
      findChecks: vi.fn().mockReturnValue(of([])),
      findEol: vi.fn().mockReturnValue(of([])),
      getProposals: vi.fn().mockReturnValue(of([])),
      getDependencies: vi.fn().mockReturnValue(of([])),
      getChanges: vi.fn().mockReturnValue(of([])),
      findDecisions: vi.fn().mockReturnValue(of([]))
    };

    await TestBed.configureTestingModule({
      imports: [ComponentDetailComponent],
      providers: [
        provideZoneChangeDetection({eventCoalescing: true}),
        provideRouter([]),
        provideTranslateService({fallbackLang: 'en'}),
        {
          provide: ComponentRepo,
          useValue: {getById: repos['getById'], getProposals: repos['getProposals'],
                     getDependencies: repos['getDependencies'], getChanges: repos['getChanges']}
        },
        {provide: EnvironmentRepo, useValue: {findAllByProject: repos['findEnvironments']}},
        {provide: ComponentEnvironmentRepo, useValue: {findAllByComponent: repos['findCells'],
                                                       createOrUpdate: vi.fn(), unplan: vi.fn()}},
        {provide: TaskRepo, useValue: {findDecisionsByComponent: repos['findDecisions']}},
        {provide: VersionChangeRepo, useValue: {findAllByComponent: repos['findHistory']}},
        {provide: UpdateCheckRepo, useValue: {findAllByComponent: repos['findChecks'], checkNow: vi.fn()}},
        {provide: EolStatusRepo, useValue: {findAllByComponent: repos['findEol'], checkNow: vi.fn()}}
      ]
    }).compileComponents();

    TestBed.inject(TranslateService).setTranslation('en', {
      'check.never': 'Not checked yet',
      'check.state.ERROR': 'Error',
      'check.field.errorMessage': 'Error',
      'check.field.state': 'Status',
      'check.section': 'Update check',
      'check.action.checkNow': 'Check now',
      'component.section.environments': 'Environments',
      'dependency.requires': 'Requires',
      'dependency.requiredBy': 'Required by',
      'constraintState.VIOLATED': 'Violated',
      'constraintState.SATISFIED': 'Satisfied',
      'matrix.cell.unknown': 'Unknown',
      'decisionState.HELD': 'Held',
      'decisionState.PLANNED': 'Planned',
      'proposal.releases': 'Versions on the way'
    });
  });

  it('shows every environment, with and without a recorded version', async () => {
    await render();

    // The environments come from the project, not from the recorded cells: an
    // environment nobody filled in yet has to be visible, or it is forgotten.
    expect(text()).toContain('Production');
    expect(text()).toContain('Staging');
    expect(text()).toContain('17.2');
  });

  it('says a component was never checked instead of leaving the block empty', async () => {
    await render();

    // An empty block would read as "checked, nothing found" — the opposite of
    // what is true right after a component is created.
    expect(text()).toContain('Not checked yet');
  });

  it('shows a failed check with its message, never as up to date', async () => {
    repos['findChecks'].mockReturnValue(of([
      new UpdateCheckDto({componentId: 'component-1', checkState: 'ERROR', errorMessage: 'Docker Hub answered 503',
                          checkedAt: '2026-09-03T05:00:00Z'})
    ]));

    await render();

    expect(text()).toContain('Error');
    expect(text()).toContain('Docker Hub answered 503');
  });

  it('keeps the page when an extra block fails', async () => {
    // Proposals, dependencies, checks and EOL are extras. The name, the
    // environments and the history are what the page is for.
    repos['getProposals'].mockReturnValue(throwError(() => new HttpErrorResponse({status: 503})));
    repos['getDependencies'].mockReturnValue(throwError(() => new HttpErrorResponse({status: 503})));
    repos['findEol'].mockReturnValue(throwError(() => new HttpErrorResponse({status: 503})));

    await render();

    expect(text()).toContain('PostgreSQL');
    expect(text()).toContain('Production');
    expect(text()).toContain('17.2');
    expect(fixture.nativeElement.querySelector('.alert-danger')).toBeNull();
  });

  it('names the environment of a dependency in both directions', async () => {
    const check = (init: Partial<DependencyCheckDto>): DependencyCheckDto =>
      new DependencyCheckDto({dependencyId: 'dependency-1', componentId: 'component-2', componentName: 'Zitadel',
                              dependsOnId: 'component-1', dependsOnName: 'PostgreSQL',
                              environmentId: 'environment-1', versionRange: '>=17 <19', actualVersion: '17.2',
                              state: 'SATISFIED', ...init});
    repos['getDependencies'].mockImplementation((_id: string, incoming: boolean) =>
      of(incoming ? [check({state: 'VIOLATED', versionRange: '>=18'})] : [check({dependsOnName: 'Valkey'})]));

    await render();

    // Both directions live in one table, told apart only by the direction
    // label — and each row needs the environment name, because the same
    // dependency is answered once per environment.
    expect(text()).toContain('Requires');
    expect(text()).toContain('Required by');
    expect(text()).toContain('Production');
    expect(text()).toContain('Violated');
    expect(text()).toContain('Satisfied');
  });

  it('marks a held component, with the reason it is held', async () => {
    repos['findDecisions'].mockReturnValue(of([
      new UpdateDecisionDto({id: 'decision-1', componentId: 'component-1', state: 'HELD', note: 'Last free version',
                             decidedAt: '2026-10-25T10:00:00Z'})
    ]));

    await render();

    // The reason is the point of a hold: without it nobody dares to lift it later.
    expect(text()).toContain('Held');
    expect(text()).toContain('Last free version');
  });

  it('shows the rollout planned for an environment', async () => {
    repos['findCells'].mockReturnValue(of([
      new ComponentEnvironmentDto({id: 'cell-1', componentId: 'component-1', environmentId: 'environment-1',
                                   currentVersion: '17.2', active: true, movingTag: false, packages: [],
                                   plannedVersion: '17.4', plannedFor: '2026-11-01', plannedNote: 'Sunday window'})
    ]));

    await render();

    expect(text()).toContain('Planned');
    expect(text()).toContain('17.4');
    expect(text()).toContain('Sunday window');
  });

  it('links every version on the way to the target', async () => {
    repos['getProposals'].mockReturnValue(of([
      new UpgradeProposalDto({environmentId: 'environment-1', environmentName: 'Production', currentVersion: '17.2',
                              target: '17.4', needsMaintenanceWindow: false,
                              releases: [new ReleaseLinkDto({version: '17.3', url: 'https://example.com/17.3'}),
                                         new ReleaseLinkDto({version: '17.4'})]})
    ]));

    await render();

    // Reading these for breaking changes is the slow part of an update; a version without a place for its notes
    // is still named.
    const link = fixture.nativeElement.querySelector('a[href="https://example.com/17.3"]') as HTMLAnchorElement | null;
    expect(link).not.toBeNull();
    expect(link?.textContent).toContain('17.3');
    expect(text()).toContain('Versions on the way');
    expect((fixture.nativeElement.querySelector('details.vc-releases') as HTMLDetailsElement).open).toBe(true);
  });

  it('folds a long list of versions on the way, so the rest of the page stays in view', async () => {
    const releases = ['17.1', '17.2', '17.3', '17.4', '17.5', '17.6', '17.7'].map((version) => new ReleaseLinkDto({version}));
    repos['getProposals'].mockReturnValue(of([
      new UpgradeProposalDto({environmentId: 'environment-1', environmentName: 'Production', currentVersion: '17.0',
                              target: '17.7', needsMaintenanceWindow: false, releases})
    ]));

    await render();

    const list = fixture.nativeElement.querySelector('details.vc-releases') as HTMLDetailsElement;
    expect(list.open).toBe(false);
    expect(list.querySelector('summary')?.textContent).toContain('(7)');
  });

  it('lists what was changed on the component', async () => {
    repos['getChanges'].mockReturnValue(of([
      new ComponentChangeDto({id: 'change-1', componentId: 'component-1', componentName: 'PostgreSQL',
                              changedAt: '2026-10-25T10:00:00Z', field: 'maxStep', oldValue: 'MINOR', newValue: 'MAJOR'})
    ]));

    await render();

    expect(text()).toContain('maxStep');
    expect(text()).toContain('MINOR');
    expect(text()).toContain('MAJOR');
  });
});
