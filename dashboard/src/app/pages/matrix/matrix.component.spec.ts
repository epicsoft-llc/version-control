import {provideZoneChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ProjectRepo} from '../../repositories/api/project.repo';
import {UpdateCheckRepo} from '../../repositories/api/update-check.repo';
import {TaskRepo} from '../../repositories/api/task.repo';
import {EnvironmentDto} from '../../repositories/dto/environment.dto';
import {MatrixCellDto, MatrixDto, MatrixRowDto} from '../../repositories/dto/matrix.dto';
import {MatrixCellState} from '../../repositories/dto/enums.dto';
import {ProjectDto} from '../../repositories/dto/project.dto';
import {UpdateCheckDto} from '../../repositories/dto/update-check.dto';
import {UpdateDecisionDto} from '../../repositories/dto/update-decision.dto';
import {SettingsDto} from '../../repositories/dto/settings.dto';
import {VulnerabilityFindingDto, VulnerabilityReportDto} from '../../repositories/dto/vulnerability.dto';
import {VulnerabilityRepo} from '../../repositories/api/vulnerability.repo';
import {ProjectSelectionService} from '../../services/project-selection/project-selection.service';
import {SettingsService} from '../../services/settings/settings.service';
import {MatrixComponent} from './matrix.component';

/**
 * The four cell states, which are the reason this table exists. "Switched off
 * here" and "nobody recorded a version" look alike in a table and mean opposite
 * things: one is a decision, the other is a gap. A matrix that draws them the
 * same way is worse than no matrix, because it is believed.
 */
describe('MatrixComponent', () => {

  let fixture: ComponentFixture<MatrixComponent>;
  let getMatrix: ReturnType<typeof vi.fn>;
  let findChecks: ReturnType<typeof vi.fn>;
  let findProjects: ReturnType<typeof vi.fn>;
  let findDecisions: ReturnType<typeof vi.fn>;
  let findVulnerabilities: ReturnType<typeof vi.fn>;

  const cells = (): HTMLTableCellElement[] =>
    [...fixture.nativeElement.querySelectorAll('tbody tr:first-child td')] as HTMLTableCellElement[];

  // The copy button carries a title of its own; the state of a cell is told by the rest.
  const titles = (): (string | null)[] => cells().map((cell) => {
    const holder = cell.querySelector('[title]:not(.vc-copy)');
    return holder ? holder.getAttribute('title') : null;
  });

  /**
   * The environment id is passed in, never derived from the state: two cells of
   * the same state would otherwise share an id, the row would model one
   * environment twice, and everything the table keys by environment would go
   * untested behind a green suite.
   */
  function cell(environmentId: string, state: MatrixCellState, init: Partial<MatrixCellDto> = {}): MatrixCellDto {
    return new MatrixCellDto({environmentId, state, movingTag: false, deviating: false, ...init});
  }

  function matrix(...list: MatrixCellDto[]): MatrixDto {
    return new MatrixDto({
      projectId: 'project-1',
      environments: list.map((c, index) => new EnvironmentDto({id: c.environmentId, name: 'Environment ' + index,
                                                               production: index === list.length - 1})),
      rows: [new MatrixRowDto({componentId: 'component-1', componentName: 'PostgreSQL', sourceType: 'DOCKER_HUB',
                               deviating: false, cells: list})]
    });
  }

  async function render(dto: MatrixDto, checks: UpdateCheckDto[] = []): Promise<void> {
    getMatrix.mockReturnValue(of(dto));
    findChecks.mockReturnValue(of(checks));
    fixture = TestBed.createComponent(MatrixComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    getMatrix = vi.fn();
    findChecks = vi.fn();
    findProjects = vi.fn().mockReturnValue(of([new ProjectDto({id: 'project-1', name: 'Platform'})]));
    findDecisions = vi.fn().mockReturnValue(of([]));
    findVulnerabilities = vi.fn().mockReturnValue(of([]));
    // The chosen project outlives a page; without this one test would choose for the next.
    localStorage.clear();

    await TestBed.configureTestingModule({
      imports: [MatrixComponent],
      providers: [
        provideZoneChangeDetection({eventCoalescing: true}),
        provideRouter([]),
        provideTranslateService({fallbackLang: 'en'}),
        {provide: ProjectRepo, useValue: {findAll: findProjects, getMatrix}},
        {provide: UpdateCheckRepo, useValue: {findAllByProject: findChecks}},
        {provide: TaskRepo, useValue: {findDecisionsByProject: findDecisions}},
        {provide: VulnerabilityRepo, useValue: {findAllByProject: findVulnerabilities}}
      ]
    }).compileComponents();

    TestBed.inject(TranslateService).setTranslation('en', {
      'matrix.cell.inactive': 'Not used here',
      'matrix.cell.notRecorded': 'Not recorded',
      'matrix.cell.unknown': 'Version unknown',
      'matrix.cell.unknownShort': '?',
      'matrix.movingTag': 'Moving tag',
      'matrix.updateAvailable': 'Update available',
      'check.state.ERROR': 'Error',
      'decisionState.HELD': 'Held',
      'decisionState.AUTO_UPDATE': 'Auto-update',
      'decisionState.PLANNED': 'Planned',
      'vulnerability.section': 'Known vulnerabilities'
    });
  });

  it('marks the cell whose version has a known vulnerability, in red when it is exploited', async () => {
    TestBed.inject(SettingsService).load(of(new SettingsDto({debianVersions: 'SHORT', vulnerabilities: true}))).subscribe();
    findVulnerabilities.mockReturnValue(of([new VulnerabilityReportDto({
      componentId: 'component-1', checkedAt: '2026-10-27T05:00:00Z', checkState: 'OK', uncomparableEnvironmentIds: [],
      findings: [new VulnerabilityFindingDto({environmentId: 'production', version: '17.2', cve: 'CVE-2026-1002',
                                              score: 8.8, severity: 'HIGH', knownExploited: true})]
    })]));

    await render(matrix(cell('development', 'RECORDED', {currentVersion: '17.6'}),
                        cell('production', 'RECORDED', {currentVersion: '17.2'})));

    const [development, production] = cells();
    expect(development.querySelector('.bi-shield-exclamation')).toBeNull();
    const marker = production.querySelector('a.text-danger');
    expect(marker?.getAttribute('title')).toBe('Known vulnerabilities: CVE-2026-1002');
    expect(marker?.textContent).toContain('1');
  });

  it('asks for no vulnerabilities without a Dependency-Check database', async () => {
    await render(matrix(cell('production', 'RECORDED', {currentVersion: '17.2'})));

    expect(findVulnerabilities).not.toHaveBeenCalled();
  });

  it('tells the four cell states apart', async () => {
    await render(matrix(cell('development', 'RECORDED', {currentVersion: '17.2'}),
                        cell('staging', 'UNKNOWN'),
                        cell('demo', 'INACTIVE'),
                        cell('production', 'NOT_RECORDED')));

    // The last cell of the row is the "available" column, not an environment.
    const [recorded, unknown, inactive] = cells();
    expect(recorded.textContent).toContain('17.2');
    // Unknown is offered for editing — somebody can answer it. The two empty
    // ones cannot be told apart by their text, so each carries its own title.
    expect(unknown.querySelector('button')).not.toBeNull();
    expect(inactive.querySelector('button')).toBeNull();
    expect(titles().slice(0, 4)).toEqual([null, 'Version unknown', 'Not used here', 'Not recorded']);
  });

  it('opens on the project chosen last, not on the first one', async () => {
    findProjects.mockReturnValue(of([new ProjectDto({id: 'project-1', name: 'Platform'}),
                                     new ProjectDto({id: 'project-2', name: 'Infra'})]));
    // What a reload or the way back from a component page leaves behind.
    TestBed.inject(ProjectSelectionService).select('project-2');

    await render(matrix(cell('production', 'RECORDED', {currentVersion: '17.2'})));

    expect(getMatrix).toHaveBeenCalledWith('project-2');
    expect(getMatrix).not.toHaveBeenCalledWith('project-1');
  });

  it('marks a moving tag where it is deployed', async () => {
    await render(matrix(cell('staging', 'RECORDED', {currentVersion: 'latest', movingTag: true}),
                        cell('production', 'RECORDED', {currentVersion: '17.2'})));

    // What a moving tag points at changes without this table changing with it,
    // so the row has to say so where the tag runs.
    expect(cells()[0].querySelector('.bi-exclamation-triangle')).not.toBeNull();
    expect(cells()[1].querySelector('.bi-exclamation-triangle')).toBeNull();
  });

  it('offers the update while one environment is still behind', async () => {
    // Half way through a rollout: staging already moved, production has not.
    await render(matrix(cell('staging', 'RECORDED', {currentVersion: '17.4'}),
                        cell('production', 'RECORDED', {currentVersion: '17.2'})),
                 [new UpdateCheckDto({componentId: 'component-1', checkState: 'OK', latestVersion: '17.4'})]);

    const available = fixture.nativeElement.querySelector('tbody tr td:last-child') as HTMLElement;
    expect(available.textContent).toContain('17.4');
    expect(available.querySelector('.bi-arrow-up-circle')).not.toBeNull();
  });

  it('says nothing is available when every environment runs the newest version', async () => {
    await render(matrix(cell('staging', 'RECORDED', {currentVersion: '17.4'}),
                        cell('production', 'RECORDED', {currentVersion: '17.4'})),
                 [new UpdateCheckDto({componentId: 'component-1', checkState: 'OK', latestVersion: '17.4'})]);

    const available = fixture.nativeElement.querySelector('tbody tr td:last-child') as HTMLElement;
    expect(available.textContent).toContain('17.4');
    expect(available.querySelector('.bi-arrow-up-circle')).toBeNull();
  });

  it('shows a failed check as a failure, never as up to date', async () => {
    await render(matrix(cell('production', 'RECORDED', {currentVersion: '17.2'})),
                 [new UpdateCheckDto({componentId: 'component-1', checkState: 'ERROR',
                                      errorMessage: 'Docker Hub answered 503'})]);

    // The rule the whole check exists for: a source that could not be reached
    // says so. Blank here would read as "nothing newer out there", which is the
    // one thing nobody knows at that moment.
    const available = fixture.nativeElement.querySelector('tbody tr td:last-child') as HTMLElement;
    expect(available.textContent).toContain('Error');
    expect(available.querySelector('.bi-x-octagon')?.getAttribute('title')).toBe('Docker Hub answered 503');
  });

  it('offers the update by difference, not by comparison', async () => {
    // Deliberate: the matrix compares recorded against reported and leaves how
    // far apart two versions are to the step rules in the backend. A recorded
    // version ahead of the last check therefore also shows the arrow — which is
    // honest, because what the source reports is then out of date, not the
    // environment.
    await render(matrix(cell('production', 'RECORDED', {currentVersion: '18.0'})),
                 [new UpdateCheckDto({componentId: 'component-1', checkState: 'OK', latestVersion: '17.4'})]);

    const available = fixture.nativeElement.querySelector('tbody tr td:last-child') as HTMLElement;
    expect(available.querySelector('.bi-arrow-up-circle')).not.toBeNull();
  });

  it('keeps the matrix when the checks cannot be loaded', async () => {
    getMatrix.mockReturnValue(of(matrix(cell('production', 'RECORDED', {currentVersion: '17.2'}))));
    findChecks.mockReturnValue(throwError(() => new HttpErrorResponse({status: 503})));
    fixture = TestBed.createComponent(MatrixComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // The available column is auxiliary; the deployed versions are what people
    // came for, and losing the whole screen over the extra column would be the
    // wrong trade.
    expect(cells()[0].textContent).toContain('17.2');
    expect(fixture.nativeElement.querySelector('.alert-danger')).toBeNull();
  });

  it('marks a held component and asks for no update', async () => {
    findDecisions.mockReturnValue(of([
      new UpdateDecisionDto({id: 'decision-1', componentId: 'component-1', state: 'HELD', note: 'Last free version',
                             decidedAt: '2026-10-25T10:00:00Z'})
    ]));

    await render(matrix(cell('production', 'RECORDED', {currentVersion: '3.76.1'})),
                 [new UpdateCheckDto({componentId: 'component-1', checkState: 'OK', latestVersion: '3.97.2',
                                      checkedAt: '2026-10-25T05:00:00Z'})]);

    // The row stays on its version on purpose; an arrow would call for an update nobody is going to make.
    const available = cells().at(-1) as HTMLTableCellElement;
    expect(available.textContent).toContain('Held');
    expect(available.querySelector('[title="Last free version"]')).not.toBeNull();
    expect(available.querySelector('[title="Update available"]')).toBeNull();
  });

  it('marks a component that updates itself and asks for no update', async () => {
    findDecisions.mockReturnValue(of([
      new UpdateDecisionDto({id: 'decision-1', componentId: 'component-1', state: 'AUTO_UPDATE', decidedAt: '2026-10-25T10:00:00Z'})
    ]));

    await render(matrix(cell('production', 'RECORDED', {currentVersion: '1.104.1'})),
                 [new UpdateCheckDto({componentId: 'component-1', checkState: 'OK', latestVersion: '1.104.2',
                                      checkedAt: '2026-10-25T05:00:00Z'})]);

    // It installs the newer version by itself; an arrow would ask for work nobody has to do.
    const available = cells().at(-1) as HTMLTableCellElement;
    expect(available.textContent).toContain('Auto-update');
    expect(available.querySelector('.bi-arrow-repeat')).not.toBeNull();
    expect(available.querySelector('[title="Update available"]')).toBeNull();
  });

  it('shows no version for a component that updates itself', async () => {
    findDecisions.mockReturnValue(of([
      new UpdateDecisionDto({id: 'decision-1', componentId: 'component-1', state: 'AUTO_UPDATE', decidedAt: '2026-10-25T10:00:00Z'})
    ]));

    await render(matrix(cell('production', 'RECORDED', {currentVersion: '1.104.1', deviating: true})));

    // What was recorded last is not what runs, and two hosts a day apart are no deviation.
    const production = cells()[0];
    expect(production.textContent).not.toContain('1.104.1');
    expect(production.querySelector('.bi-arrow-repeat')).not.toBeNull();
    expect(production.classList.contains('table-warning')).toBe(false);
  });

  it('shows a planned rollout in its cell', async () => {
    await render(matrix(cell('production', 'RECORDED', {currentVersion: '17.2', plannedVersion: '17.4',
                                                        plannedFor: '2026-11-01'})));

    expect(titles()[0]).toContain('Planned: 17.4');
  });
});
