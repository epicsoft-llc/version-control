import {provideZoneChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService, TranslateService} from '@ngx-translate/core';
import {of, Subject, throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ProjectRepo} from '../../repositories/api/project.repo';
import {TaskRepo} from '../../repositories/api/task.repo';
import {ComponentChangeRepo} from '../../repositories/api/component-change.repo';
import {ComponentChangeDto} from '../../repositories/dto/component-change.dto';
import {DependencyCheckDto} from '../../repositories/dto/dependency.dto';
import {ProjectDto} from '../../repositories/dto/project.dto';
import {TaskEntryDto, TaskListDto, TaskSection, TaskSectionDto} from '../../repositories/dto/task.dto';
import {TasksComponent} from './tasks.component';

/**
 * The start page. What is tested is what the reader can act on: which sections
 * offer a decision, that a violated dependency is spelled out rather than shown
 * as an id, and that a failure to load says so instead of looking like an empty
 * list — an empty task list means "nothing to do", which is the one wrong
 * answer here.
 */
describe('TasksComponent', () => {

  let fixture: ComponentFixture<TasksComponent>;
  let getByProject: ReturnType<typeof vi.fn>;
  let findRecentChanges: ReturnType<typeof vi.fn>;

  const text = (): string => fixture.nativeElement.textContent as string;

  const rows = (): HTMLTableRowElement[] =>
    [...fixture.nativeElement.querySelectorAll('tbody tr')] as HTMLTableRowElement[];

  const decideButtons = (): HTMLButtonElement[] =>
    [...fixture.nativeElement.querySelectorAll('tbody .btn')] as HTMLButtonElement[];

  function entry(name: string, init: Partial<TaskEntryDto> = {}): TaskEntryDto {
    return new TaskEntryDto({componentId: 'component-' + name, componentName: name, ...init});
  }

  function list(...sections: [TaskSection, TaskEntryDto[]][]): TaskListDto {
    return new TaskListDto({
      projectId: 'project-1',
      sections: sections.map(([section, entries]) => new TaskSectionDto({section, entries}))
    });
  }

  async function render(answer: TaskListDto): Promise<void> {
    getByProject.mockReturnValue(of(answer));
    fixture = TestBed.createComponent(TasksComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    getByProject = vi.fn();
    findRecentChanges = vi.fn().mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [TasksComponent],
      providers: [
        // The same change detection the application configures. Without it the
        // test bed runs zoneless, and a test that waits for Angular to render
        // by itself would fail for a reason the application does not have.
        provideZoneChangeDetection({eventCoalescing: true}),
        provideRouter([]),
        provideTranslateService({fallbackLang: 'en'}),
        {provide: ProjectRepo, useValue: {findAll: () => of([new ProjectDto({id: 'project-1', name: 'Platform'})])}},
        {provide: TaskRepo, useValue: {getByProject}},
        {provide: ComponentChangeRepo, useValue: {findRecentByProject: findRecentChanges}}
      ]
    }).compileComponents();

    // The bundle is not part of this test — only that the interface asks for
    // the right keys and puts the data around them. Anything unset renders as
    // the key, which would be a silent pass.
    TestBed.inject(TranslateService).setTranslation('en', {
      'taskSection.UPDATE_OPEN': 'Update open',
      'taskSection.DEPENDENCY_VIOLATED': 'Dependency violated',
      'taskSection.EOL_WARNING': 'Support ends soon',
      'tasks.action.decide': 'Decide',
      'dependency.needs': 'needs',
      'constraintState.VIOLATED': 'Violated',
      'taskSection.ROLLOUT_PLANNED': 'Rollout planned',
      'plan.field.date': 'Planned for',
      'tasks.since': 'Since',
      'tasks.recentChanges': 'Latest changes'
    });
  });

  it('offers a decision where one can be made, and only there', async () => {
    await render(list(['UPDATE_OPEN', [entry('PostgreSQL')]],
                      ['EOL_WARNING', [entry('Zitadel')]]));

    expect(rows().length).toBe(2);
    // Blocked and postponed entries are decidable as well — somebody may decide
    // to skip a blocked update. An EOL warning is not: there is nothing to
    // decide, the date is what it is.
    expect(decideButtons().length).toBe(1);
    expect(rows()[0].textContent).toContain('PostgreSQL');
    expect(rows()[0].querySelector('.btn')).not.toBeNull();
    expect(rows()[1].querySelector('.btn')).toBeNull();
  });

  it('spells a violated dependency out', async () => {
    const violation = new DependencyCheckDto({
      dependencyId: 'dependency-1',
      componentId: 'component-Zitadel',
      componentName: 'Zitadel',
      dependsOnName: 'PostgreSQL',
      versionRange: '>=14 <18',
      actualVersion: '18.0',
      state: 'VIOLATED'
    });

    await render(list(['DEPENDENCY_VIOLATED', [entry('Zitadel', {environmentName: 'Production',
                                                                 constraints: [violation]})]]));

    // The sentence is composed here and not in the backend, so this is where it
    // can go missing. Every part of it has to be on screen: who needs what, in
    // which version, and what is actually deployed.
    const row = rows()[0].textContent ?? '';
    expect(row).toContain('Zitadel');
    expect(row).toContain('Production');
    expect(row).toContain('18.0');
    expect(row).toContain('needs');
    expect(row).toContain('PostgreSQL');
    expect(row).toContain('>=14 <18');
    expect(row).toContain('Violated');
  });

  it('shows the version step of an open update', async () => {
    await render(list(['UPDATE_OPEN', [entry('PostgreSQL', {currentVersion: '17.2', latestVersion: '17.4'})]]));

    const row = rows()[0].textContent ?? '';
    expect(row).toContain('17.2');
    expect(row).toContain('17.4');
  });

  it('shows what arrives after the first render', async () => {
    // The one thing no other test here catches, because they all call
    // detectChanges by hand: this Angular version compiles every component as
    // OnPush unless told otherwise, and a view that nothing marks dirty keeps
    // the empty page it started with. The application then loads its data,
    // shows a heading and nothing else, and reports no error anywhere.
    const answer = new Subject<TaskListDto>();
    getByProject.mockReturnValue(answer);
    fixture = TestBed.createComponent(TasksComponent);
    fixture.autoDetectChanges();
    await fixture.whenStable();
    expect(rows().length).toBe(0);

    // Inside the zone, the way an HTTP answer arrives in the running
    // application — and then Angular has to render it on its own.
    fixture.ngZone!.run(() =>
      answer.next(list(['UPDATE_OPEN', [entry('PostgreSQL', {currentVersion: '17.2', latestVersion: '17.4'})]])));
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();

    // Rendered without a single detectChanges of our own.
    expect(rows().length).toBe(1);
    expect(rows()[0].textContent).toContain('PostgreSQL');
  });

  it('says so when the list could not be loaded', async () => {
    getByProject.mockReturnValue(throwError(() => new HttpErrorResponse({error: {message: 'Service unavailable'},
                                                                        status: 503})));
    fixture = TestBed.createComponent(TasksComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // An empty page would read as "nothing to do" — the one answer that must
    // never be given by accident.
    expect(fixture.nativeElement.querySelector('.alert-danger')).not.toBeNull();
    expect(text()).toContain('Service unavailable');
    expect(rows().length).toBe(0);
  });

  it('shows a planned rollout with its day, and offers no decision for it', async () => {
    await render(list(['ROLLOUT_PLANNED', [entry('Nexus', {environmentName: 'Production', currentVersion: '3.76.1',
                                                          latestVersion: '3.77.0', since: '2026-11-01T12:00:00Z'})]]));

    // The plan is the answer already; a decide button would ask the same question again. The copy buttons
    // beside the two versions are buttons too, so the decision is looked for by its label.
    expect(decideButtons().some((button) => button.textContent?.includes('Decide'))).toBe(false);
    const row = rows()[0].textContent ?? '';
    expect(row).toContain('3.77.0');
    expect(row).toContain('Planned for');
    expect(row).not.toContain('Since');
  });

  it('lists the latest changes of the project below the tasks', async () => {
    findRecentChanges.mockReturnValue(of([
      new ComponentChangeDto({id: 'change-1', componentId: 'component-Nexus', componentName: 'Nexus',
                              changedAt: '2026-10-25T10:00:00Z', field: 'decision', oldValue: 'SKIPPED', newValue: 'HELD'})
    ]));

    await render(list());

    // For a reader who looks in now and then: what happened since.
    expect(text()).toContain('Latest changes');
    expect(text()).toContain('Nexus');
    expect(text()).toContain('SKIPPED');
    expect(text()).toContain('HELD');
  });
});
