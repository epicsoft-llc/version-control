package one.epicsoft.vc.facade;

import one.epicsoft.vc.AbstractIntegrationTest;
import one.epicsoft.vc.dto.ComponentDependencyRequest;
import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentEnvironmentDto;
import one.epicsoft.vc.dto.ComponentEnvironmentRequest;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.EnvironmentDto;
import one.epicsoft.vc.dto.EnvironmentRequest;
import one.epicsoft.vc.dto.ProjectRequest;
import one.epicsoft.vc.dto.RolloutPlanRequest;
import one.epicsoft.vc.dto.TaskListDto;
import one.epicsoft.vc.dto.TaskSectionDto;
import one.epicsoft.vc.dto.UpdateDecisionRequest;
import one.epicsoft.vc.dto.VersionChangeRequest;
import one.epicsoft.vc.enums.CheckState;
import one.epicsoft.vc.enums.DecisionState;
import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.enums.TaskSection;
import one.epicsoft.vc.exception.AppValidationException;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.EolStatusEntity;
import one.epicsoft.vc.jpa.entity.UpdateCheckEntity;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.EolStatusService;
import one.epicsoft.vc.service.UpdateCheckService;
import one.epicsoft.vc.service.UpdateDecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The list that answers "what has to be done", and the decisions that keep
 * entries off it — for exactly as long as they are about the version that is on
 * the table.
 */
@Transactional
class TaskListFacadeIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private ProjectFacade projectFacade;

  @Autowired
  private EnvironmentFacade environmentFacade;

  @Autowired
  private ComponentFacade componentFacade;

  @Autowired
  private ComponentEnvironmentFacade componentEnvironmentFacade;

  @Autowired
  private UpdateDecisionFacade updateDecisionFacade;

  @Autowired
  private TaskListFacade taskListFacade;

  @Autowired
  private UpdateCheckService updateCheckService;

  @Autowired
  private EolStatusService eolStatusService;

  @Autowired
  private UpdateDecisionService updateDecisionService;

  @Autowired
  private ComponentService componentService;

  @Autowired
  private VersionChangeFacade versionChangeFacade;

  @Test
  void listsAnAvailableUpdateNobodyDecidedAbout() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(component, "17.4", CheckState.OK, Instant.now());

    final TaskListDto list = this.taskListFacade.getByProject(projectId);

    final TaskSectionDto section = this.section(list, TaskSection.UPDATE_OPEN);
    assertEquals(1, section.getEntries().size());
    assertEquals("17.2", section.getEntries().getFirst().getCurrentVersion());
    assertEquals("17.4", section.getEntries().getFirst().getLatestVersion());
  }

  @Test
  void takesAPlannedUpdateOffTheList() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(component, "17.4", CheckState.OK, Instant.now());
    this.decide(component.getId(), DecisionState.PLANNED, null);

    // A decision is an answer. Leaving it on the list would train people to
    // ignore the list.
    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.UPDATE_OPEN.equals(section.getSection())));
  }

  @Test
  void bringsBackAPostponementWhoseDateHasPassed() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(component, "17.4", CheckState.OK, Instant.now());
    this.decide(component.getId(), DecisionState.POSTPONED, Instant.now().plus(7, ChronoUnit.DAYS));
    // Postponing into the past is refused, so the date is moved instead — which
    // is what the passing of a week does anyway.
    this.expireSnooze(component.getId());

    assertEquals(1, this.section(this.taskListFacade.getByProject(projectId), TaskSection.SNOOZE_EXPIRED)
                        .getEntries().size());
  }

  @Test
  void reopensADecisionWhenANewerVersionArrives() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(component, "17.4", CheckState.OK, Instant.now());
    this.decide(component.getId(), DecisionState.SKIPPED, null);

    final boolean reopened = this.updateDecisionFacade.reopenIfNewerVersion(component.getId(), "17.5");

    // Otherwise skipping 17.4 quietly skips everything after it as well.
    assertTrue(reopened);
    assertEquals(DecisionState.OPEN,
                 this.updateDecisionService.findByComponent(component.getId()).orElseThrow().getState());
    // The note survives: what somebody wrote about the last version is the most
    // useful thing there is when deciding about the next.
    assertEquals("Not this quarter",
                 this.updateDecisionService.findByComponent(component.getId()).orElseThrow().getNote());
  }

  @Test
  void leavesADecisionAloneWhileItIsAboutTheVersionOnTheTable() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(component, "17.4", CheckState.OK, Instant.now());
    this.decide(component.getId(), DecisionState.SKIPPED, null);

    assertFalse(this.updateDecisionFacade.reopenIfNewerVersion(component.getId(), "17.4"));
  }

  @Test
  void waitsOutTheGracePeriodBeforeReportingAFailingCheck() {
    final UUID projectId = this.project();
    final ComponentDto fresh = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(fresh, null, CheckState.ERROR, Instant.now());

    // A single network hiccup is not a task, and a list full of them stops being read.
    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.CHECK_ERROR.equals(section.getSection())));
  }

  @Test
  void reportsACheckThatHasBeenBlindForLongerThanTheGracePeriod() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(component, null, CheckState.ERROR, Instant.now().minus(10, ChronoUnit.DAYS));

    assertEquals(1, this.section(this.taskListFacade.getByProject(projectId), TaskSection.CHECK_ERROR)
                        .getEntries().size());
  }

  @Test
  void reportsAMovingTagOnlyInProduction() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));
    final EnvironmentDto production = this.environment(projectId, "Production", true);
    final EnvironmentDto staging = this.environment(projectId, "Staging", false);
    this.cell(component.getId(), production.getId(), "17-alpine");
    this.cell(component.getId(), staging.getId(), "17-alpine");

    final TaskSectionDto section = this.section(this.taskListFacade.getByProject(projectId),
                                                TaskSection.MOVING_TAG_IN_PRODUCTION);

    // Allowed anywhere, worth a task only where the image can change unnoticed.
    assertEquals(1, section.getEntries().size());
    assertEquals("Production", section.getEntries().getFirst().getEnvironmentName());
  }

  @Test
  void keepsTheSectionsInTheOrderTheyAreRead() {
    final UUID projectId = this.project();
    final ComponentDto behind = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(behind, "17.4", CheckState.OK, Instant.now());
    final ComponentDto failing = this.componentWithVersion(projectId, "1.0");
    this.recordCheck(failing, null, CheckState.ERROR, Instant.now().minus(10, ChronoUnit.DAYS));

    final List<TaskSection> order = this.sections(this.taskListFacade.getByProject(projectId)).stream()
                                        .map(TaskSectionDto::getSection).toList();

    // What is behind comes before what is merely not answering.
    assertEquals(List.of(TaskSection.UPDATE_OPEN, TaskSection.CHECK_ERROR), order);
  }

  @Test
  void keepsAPendingUpdateOnTheListWhileTheCheckIsFailing() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(component, "17.6", CheckState.OK, Instant.now());
    // The source answers with a 503 on the next tick; the version it found last
    // time is still what is available.
    this.recordCheck(component, "17.6", CheckState.ERROR, Instant.now());

    // Dropping the entry would leave the start page reporting nothing to do for
    // a component that is two versions behind.
    assertEquals(1, this.section(this.taskListFacade.getByProject(projectId), TaskSection.UPDATE_OPEN)
                        .getEntries().size());
  }

  @Test
  void leavesAMovingTagOutOfTheUpdateSection() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));
    final EnvironmentDto staging = this.environment(projectId, "Staging", false);
    this.cell(component.getId(), staging.getId(), "17-alpine");
    this.recordCheck(component, "17.6", CheckState.OK, Instant.now());

    // 17-alpine never equals 17.6, so the entry could never be cleared; the
    // moving tag has a section of its own where it can be acted on.
    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.UPDATE_OPEN.equals(section.getSection())));
  }

  @Test
  void doesNotCallAnEnvironmentBehindWhenItIsAhead() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "18.0");
    // A rule pinned to major 17 reports 17.6 as the newest allowed version.
    this.recordCheck(component, "17.6", CheckState.OK, Instant.now());

    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.UPDATE_OPEN.equals(section.getSection())));
  }

  @Test
  void listsEveryEnvironmentThatIsBehind() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));
    final EnvironmentDto production = this.environment(projectId, "Production", true);
    final EnvironmentDto staging = this.environment(projectId, "Staging", false);
    this.cell(component.getId(), production.getId(), "17.2");
    this.cell(component.getId(), staging.getId(), "17.4");
    this.recordCheck(component, "17.6", CheckState.OK, Instant.now());

    final TaskSectionDto section = this.section(this.taskListFacade.getByProject(projectId), TaskSection.UPDATE_OPEN);

    // Reporting one of them would hide the other and change between two reloads.
    assertEquals(2, section.getEntries().size());
    // What is live comes first.
    assertEquals("Production", section.getEntries().getFirst().getEnvironmentName());
  }

  @Test
  void leavesADecisionAloneWhenTheReportedVersionGotLower() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(component, "18.0", CheckState.OK, Instant.now());
    this.decide(component.getId(), DecisionState.SKIPPED, null);

    // Tightening a rule lowers what the check reports; nothing new happened.
    assertFalse(this.updateDecisionFacade.reopenIfNewerVersion(component.getId(), "17.6"));
  }

  @Test
  void refusesAPostponementIntoThePast() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");

    // Same as no date: the entry is back before the reader has closed the dialog.
    assertThrows(AppValidationException.class,
                 () -> this.decide(component.getId(), DecisionState.POSTPONED,
                                   Instant.now().minus(1, ChronoUnit.HOURS)));
  }

  @Test
  void putsAnEndedSupportAboveEverythingElse() {
    final UUID projectId = this.project();
    final ComponentDto behind = this.componentWithVersion(projectId, "17.2");
    this.recordCheck(behind, "17.4", CheckState.OK, Instant.now());
    final ComponentDto expired = this.componentWithVersion(projectId, "13.1");
    this.recordEol(expired, LocalDate.now().minusMonths(2), true);

    final List<TaskSection> order = this.sections(this.taskListFacade.getByProject(projectId)).stream()
                                        .map(TaskSectionDto::getSection).toList();

    // What is out of support comes before what is merely behind.
    assertEquals(TaskSection.EOL_EXCEEDED, order.getFirst());
  }

  @Test
  void separatesAWarningFromSomethingThatHasToBeDone() {
    final UUID projectId = this.project();
    final ComponentDto soon = this.componentWithVersion(projectId, "17.2");
    // Inside the warning window, outside the escalation window.
    this.recordEol(soon, LocalDate.now().plusMonths(5), false);

    assertEquals(1, this.section(this.taskListFacade.getByProject(projectId), TaskSection.EOL_WARNING)
                        .getEntries().size());
  }

  @Test
  void saysNothingAboutSupportThatEndsFarAway() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");
    this.recordEol(component, LocalDate.now().plusYears(3), false);

    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.EOL_WARNING.equals(section.getSection())
                                         || TaskSection.EOL_EXCEEDED.equals(section.getSection())));
  }

  @Test
  void proposesThePermissibleTargetRatherThanTheNewestVersion() {
    final UUID projectId = this.project();
    final ComponentRequest request = this.componentRequest(projectId, "PostgreSQL " + UUID.randomUUID());
    request.setMaxStep(MaxStep.MINOR);
    final ComponentDto component = this.componentFacade.create(request);
    final EnvironmentDto environment = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    this.cell(component.getId(), environment.getId(), "16.4");
    this.recordCheckWithCandidates(component, "18.2", List.of("16.4", "16.9", "17.2", "18.2"));

    final TaskSectionDto section = this.section(this.taskListFacade.getByProject(projectId), TaskSection.UPDATE_OPEN);

    // 16.9 is what can be done now; 18.2 is a project and belongs on the
    // component page as a note, not here as a demand.
    assertEquals("16.9", section.getEntries().getFirst().getLatestVersion());
  }

  @Test
  void raisesNoTaskWhenEverythingAvailableIsPastTheLimit() {
    final UUID projectId = this.project();
    final ComponentRequest request = this.componentRequest(projectId, "PostgreSQL " + UUID.randomUUID());
    request.setMaxStep(MaxStep.MINOR);
    final ComponentDto component = this.componentFacade.create(request);
    final EnvironmentDto environment = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    this.cell(component.getId(), environment.getId(), "16.4");
    this.recordCheckWithCandidates(component, "18.2", List.of("16.4", "18.2"));

    // A permanent red state nobody can resolve teaches people to ignore red states.
    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.UPDATE_OPEN.equals(section.getSection())));
  }

  @Test
  void proposesOnlyVersionsTheRuleAccepts() {
    final UUID projectId = this.project();
    final ComponentRequest request = this.componentRequest(projectId, "PostgreSQL " + UUID.randomUUID());
    request.setTagPattern("^\\d+\\.\\d+$");
    final ComponentDto component = this.componentFacade.create(request);
    final EnvironmentDto environment = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    this.cell(component.getId(), environment.getId(), "16.4");
    // The stored list is what the source offered, variants and all.
    this.recordCheckWithCandidates(component, "16.9", List.of("16.4", "16.9", "16.9-alpine", "16.9-bookworm"));

    final TaskSectionDto section = this.section(this.taskListFacade.getByProject(projectId), TaskSection.UPDATE_OPEN);

    // A proposal naming a variant the rule rejects is one the check itself would
    // never have reported.
    assertEquals("16.9", section.getEntries().getFirst().getLatestVersion());
  }

  @Test
  void keepsAnEndedSupportOnTheListWhileTheLookupIsFailing() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "13.1");
    this.recordEol(component, LocalDate.now().minusMonths(2), true);
    this.failEolLookup(component);

    // The dates of the last good answer are kept on failure; dropping the entry
    // would take an out-of-support component off the start page entirely.
    assertEquals(1, this.section(this.taskListFacade.getByProject(projectId), TaskSection.EOL_EXCEEDED)
                        .getEntries().size());
  }

  @Test
  void dropsAnEndedSupportOnceTheProductIsCleared() {
    final UUID projectId = this.project();
    final ComponentRequest request = this.componentRequest(projectId, "MariaDB " + UUID.randomUUID());
    request.setEolProduct("mariadb");
    request.setEolCycle("10.1");
    final ComponentDto component = this.componentFacade.create(request);
    this.recordEol(component, LocalDate.now().minusYears(6), true);

    request.setEolProduct(null);
    request.setEolCycle(null);
    this.componentFacade.update(component.getId(), request);

    // No product means the question does not apply; nothing asks again, so the old answer would stay for good.
    assertTrue(this.eolStatusService.findByComponent(component.getId()).isEmpty());
    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.EOL_EXCEEDED.equals(section.getSection())));
  }

  @Test
  void keepsTheEndOfSupportWhenSomethingElseChanges() {
    final UUID projectId = this.project();
    final ComponentRequest request = this.componentRequest(projectId, "MariaDB " + UUID.randomUUID());
    request.setEolProduct("mariadb");
    final ComponentDto component = this.componentFacade.create(request);
    this.recordEol(component, LocalDate.now().minusYears(6), true);

    request.setName("MariaDB " + UUID.randomUUID());
    this.componentFacade.update(component.getId(), request);

    assertEquals(1, this.section(this.taskListFacade.getByProject(projectId), TaskSection.EOL_EXCEEDED)
                        .getEntries().size());
  }

  @Test
  void filesABlockedUpdateAsBlockedRatherThanAsSomethingToDo() {
    final UUID projectId = this.project();
    // Major steps allowed, so 18.0 is actually proposed — and then blocked.
    final ComponentRequest databaseRequest = this.componentRequest(projectId, "PostgreSQL");
    databaseRequest.setMaxStep(MaxStep.MAJOR);
    final ComponentDto database = this.componentFacade.create(databaseRequest);
    final ComponentDto app = this.componentFacade.create(this.componentRequest(projectId, "Zitadel"));
    final EnvironmentDto production = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    this.cell(database.getId(), production.getId(), "16.4");
    this.cell(app.getId(), production.getId(), "3.1.0");
    this.dependsOn(app, database, ">=14 <18");
    this.recordCheckWithCandidates(database, "18.0", List.of("16.4", "18.0"));

    final TaskListDto list = this.taskListFacade.getByProject(projectId);

    // The start page must not ask for an upgrade the detail page says is
    // impossible; and the entry names what stands in the way.
    assertTrue(this.sections(list).stream()
                   .noneMatch(section -> TaskSection.UPDATE_OPEN.equals(section.getSection())));
    final TaskSectionDto blocked = this.section(list, TaskSection.BLOCKED);
    assertEquals(1, blocked.getEntries().size());
    // The blocker travels as data, not as an English sentence: what it reads
    // like is the interface's business, in the reader's language.
    assertEquals("Zitadel", blocked.getEntries().getFirst().getConstraints().getFirst().getComponentName());
    assertEquals(">=14 <18", blocked.getEntries().getFirst().getConstraints().getFirst().getVersionRange());
  }

  @Test
  void leavesAPrerequisiteThatIsNotRecordedHereOffTheList() {
    final UUID projectId = this.project();
    final ComponentDto database = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));
    final ComponentDto app = this.componentFacade.create(this.componentRequest(projectId, "Zitadel"));
    final EnvironmentDto production = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    // The dependent runs, the prerequisite was never recorded here.
    this.cell(app.getId(), production.getId(), "3.1.0");
    this.dependsOn(app, database, ">=14 <18");

    // A gap in the inventory, which the matrix already shows as one. Repeating
    // it here would fill the list with entries nobody can act on.
    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.DEPENDENCY_VIOLATED.equals(section.getSection())));
  }

  @Test
  void refusesToSetBlockedByHandAndAPostponementWithoutADate() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "17.2");

    assertThrows(AppValidationException.class,
                 () -> this.decide(component.getId(), DecisionState.BLOCKED, null));
    // A postponement without a date is a note nobody comes back to.
    assertThrows(AppValidationException.class,
                 () -> this.decide(component.getId(), DecisionState.POSTPONED, null));
  }

  @Test
  void refusesAHoldWithoutAReason() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "3.76.1");
    final UpdateDecisionRequest request = new UpdateDecisionRequest();
    request.setComponentId(component.getId());
    request.setState(DecisionState.HELD);

    // A hold stays for good; without the reason nobody dares to lift it later.
    assertThrows(AppValidationException.class, () -> this.updateDecisionFacade.decide(request));
  }

  @Test
  void keepsAHeldComponentOffTheListWhateverComesOut() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "3.76.1");
    this.recordCheck(component, "3.97.2", CheckState.OK, Instant.now());
    this.decide(component.getId(), DecisionState.HELD, null);

    // Unlike a skip, a newer version does not reopen it.
    assertFalse(this.updateDecisionFacade.reopenIfNewerVersion(component.getId(), "3.98.0"));
    assertEquals(DecisionState.HELD, this.updateDecisionService.findByComponent(component.getId()).orElseThrow().getState());
    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.UPDATE_OPEN.equals(section.getSection())));
  }

  @Test
  void keepsAComponentThatUpdatesItselfOffTheListWhateverComesOut() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentWithVersion(projectId, "1.104.1");
    this.recordCheck(component, "1.104.2", CheckState.OK, Instant.now());
    final UpdateDecisionRequest request = new UpdateDecisionRequest();
    request.setComponentId(component.getId());
    request.setState(DecisionState.AUTO_UPDATE);
    // Without a note: what it means is in the state itself.
    this.updateDecisionFacade.decide(request);

    assertFalse(this.updateDecisionFacade.reopenIfNewerVersion(component.getId(), "1.106.0"));
    assertEquals(DecisionState.AUTO_UPDATE, this.updateDecisionService.findByComponent(component.getId()).orElseThrow().getState());
    assertTrue(this.sections(this.taskListFacade.getByProject(projectId)).stream()
                   .noneMatch(section -> TaskSection.UPDATE_OPEN.equals(section.getSection())));
  }

  @Test
  void announcesAPlannedRolloutInsteadOfAnOpenUpdate() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "Component " + UUID.randomUUID()));
    final EnvironmentDto environment = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    final ComponentEnvironmentDto cell = this.cell(component.getId(), environment.getId(), "17.2");
    this.recordCheck(component, "17.4", CheckState.OK, Instant.now());

    this.componentEnvironmentFacade.plan(cell.getId(), this.plan("17.4", LocalDate.now().plusDays(3)));

    final TaskListDto list = this.taskListFacade.getByProject(projectId);
    // The update is answered by the plan; listing it as open as well would ask the same question twice.
    assertTrue(this.sections(list).stream().noneMatch(section -> TaskSection.UPDATE_OPEN.equals(section.getSection())));
    final TaskSectionDto planned = this.section(list, TaskSection.ROLLOUT_PLANNED);
    assertEquals("17.4", planned.getEntries().getFirst().getLatestVersion());
    assertEquals("Sunday window", planned.getEntries().getFirst().getDetail());
    // The plan reads after everything that needs doing today.
    assertEquals(TaskSection.ROLLOUT_PLANNED, list.getSections().getLast().getSection());
  }

  @Test
  void reportsAPlannedRolloutWhoseDayHasPassed() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "Component " + UUID.randomUUID()));
    final EnvironmentDto environment = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    final ComponentEnvironmentDto cell = this.cell(component.getId(), environment.getId(), "17.2");

    this.componentEnvironmentFacade.plan(cell.getId(), this.plan("17.4", LocalDate.now().minusDays(1)));

    assertEquals(1, this.section(this.taskListFacade.getByProject(projectId), TaskSection.ROLLOUT_OVERDUE).getEntries().size());
  }

  @Test
  void endsAPlanOnceItsVersionIsRecorded() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "Component " + UUID.randomUUID()));
    final EnvironmentDto environment = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    final ComponentEnvironmentDto cell = this.cell(component.getId(), environment.getId(), "17.2");
    this.componentEnvironmentFacade.plan(cell.getId(), this.plan("17.4", LocalDate.now().plusDays(3)));

    // A step on the way is not the rollout that was planned.
    this.changeVersion(cell.getId(), "17.3");
    assertEquals("17.4", this.componentEnvironmentFacade.getById(cell.getId()).getPlannedVersion());

    this.changeVersion(cell.getId(), "17.4");
    final ComponentEnvironmentDto done = this.componentEnvironmentFacade.getById(cell.getId());
    assertNull(done.getPlannedVersion());
    assertNull(done.getPlannedFor());
  }

  @Test
  void refusesAPlanForTheRecordedVersionAndForAnEnvironmentWithoutTheComponent() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "Component " + UUID.randomUUID()));
    final EnvironmentDto environment = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    final ComponentEnvironmentDto cell = this.cell(component.getId(), environment.getId(), "17.2");

    assertThrows(AppValidationException.class,
                 () -> this.componentEnvironmentFacade.plan(cell.getId(), this.plan("17.2", LocalDate.now())));

    final ComponentEnvironmentRequest off = new ComponentEnvironmentRequest();
    off.setComponentId(component.getId());
    off.setEnvironmentId(environment.getId());
    off.setActive(Boolean.FALSE);
    this.componentEnvironmentFacade.createOrUpdate(off);
    assertThrows(AppValidationException.class,
                 () -> this.componentEnvironmentFacade.plan(cell.getId(), this.plan("17.4", LocalDate.now())));
  }

  private RolloutPlanRequest plan(final String version, final LocalDate day) {
    final RolloutPlanRequest request = new RolloutPlanRequest();
    request.setPlannedVersion(version);
    request.setPlannedFor(day);
    request.setNote("Sunday window");
    return request;
  }

  private void changeVersion(final UUID cellId, final String version) {
    final VersionChangeRequest request = new VersionChangeRequest();
    request.setComponentEnvironmentId(cellId);
    request.setToVersion(version);
    this.versionChangeFacade.changeVersion(request);
  }

  private TaskSectionDto section(final TaskListDto list, final TaskSection section) {
    return this.sections(list).stream()
               .filter(entry -> section.equals(entry.getSection()))
               .findFirst()
               .orElseThrow(() -> new AssertionError("section " + section + " is missing"));
  }

  private List<TaskSectionDto> sections(final TaskListDto list) {
    return list.getSections();
  }

  /** Lets the postponement run out, without waiting a week for it. */
  private void expireSnooze(final UUID componentId) {
    final var decision = this.updateDecisionService.findByComponent(componentId).orElseThrow();
    decision.setSnoozeUntil(Instant.now().minus(1, ChronoUnit.DAYS));
    this.updateDecisionService.save(decision);
  }

  private void dependsOn(final ComponentDto component, final ComponentDto dependsOn, final String range) {
    final ComponentDependencyRequest dependency = new ComponentDependencyRequest();
    dependency.setDependsOnId(dependsOn.getId());
    dependency.setVersionRange(range);

    final ComponentRequest request = this.componentRequest(component.getProjectId(), component.getName());
    request.setMaxStep(component.getMaxStep());
    request.setDependencies(List.of(dependency));
    this.componentFacade.update(component.getId(), request);
  }

  private void decide(final UUID componentId, final DecisionState state, final Instant snoozeUntil) {
    final UpdateDecisionRequest request = new UpdateDecisionRequest();
    request.setComponentId(componentId);
    request.setState(state);
    request.setSnoozeUntil(snoozeUntil);
    request.setNote("Not this quarter");
    this.updateDecisionFacade.decide(request);
  }

  /** Writes an end-of-life result directly; the lookup itself is tested offline. */
  private void recordEol(final ComponentDto component, final LocalDate eolDate, final boolean over) {
    final EolStatusEntity status = this.eolStatusService.findByComponent(component.getId())
                                       .orElseGet(EolStatusEntity::new);
    status.setComponent(this.componentService.getById(component.getId()));
    status.setEolDate(eolDate);
    status.setEol(over);
    status.setCheckedAt(Instant.now());
    status.setCheckState(CheckState.OK);
    this.eolStatusService.save(status);
  }

  /** Lets the last end-of-life lookup fail, with its dates still on the row. */
  private void failEolLookup(final ComponentDto component) {
    final EolStatusEntity status = this.eolStatusService.findByComponent(component.getId()).orElseThrow();
    status.setCheckState(CheckState.ERROR);
    status.setErrorMessage("Request failed");
    this.eolStatusService.save(status);
  }

  private void recordCheckWithCandidates(final ComponentDto component, final String latestVersion,
                                         final List<String> candidates) {
    this.recordCheck(component, latestVersion, CheckState.OK, Instant.now());
    final UpdateCheckEntity check = this.updateCheckService.findByComponent(component.getId()).orElseThrow();
    // The stored candidate list is what makes a limited step possible at all:
    // without it only the newest version is known and the one in between cannot
    // be named.
    check.setCandidateTags(String.join("\n", candidates));
    this.updateCheckService.save(check);
  }

  /** Writes a check result directly: what the adapters do is tested without a network elsewhere. */
  private void recordCheck(final ComponentDto component, final String latestVersion, final CheckState state,
                           final Instant lastSuccess) {
    final ComponentEntity entity = this.componentService.getById(component.getId());
    final UpdateCheckEntity check = Optional.ofNullable(this.updateCheckService.findByComponent(component.getId())
                                                            .orElse(null))
                                            .orElseGet(UpdateCheckEntity::new);
    check.setComponent(entity);
    check.setCheckState(state);
    check.setLatestVersion(latestVersion);
    check.setCheckedAt(Instant.now());
    check.setLastSuccessfulCheckAt(CheckState.OK.equals(state) ? Instant.now() : lastSuccess);
    check.setErrorMessage(CheckState.ERROR.equals(state) ? "Request failed" : null);
    this.updateCheckService.save(check);
  }

  private ComponentDto componentWithVersion(final UUID projectId, final String version) {
    final ComponentDto component =
      this.componentFacade.create(this.componentRequest(projectId, "Component " + UUID.randomUUID()));
    final EnvironmentDto environment = this.environment(projectId, "Production " + UUID.randomUUID(), true);
    this.cell(component.getId(), environment.getId(), version);
    return component;
  }

  private ComponentEnvironmentDto cell(final UUID componentId, final UUID environmentId, final String version) {
    final ComponentEnvironmentRequest request = new ComponentEnvironmentRequest();
    request.setComponentId(componentId);
    request.setEnvironmentId(environmentId);
    request.setCurrentVersion(version);
    return this.componentEnvironmentFacade.createOrUpdate(request);
  }

  private UUID project() {
    final ProjectRequest request = new ProjectRequest();
    request.setName("Project " + UUID.randomUUID());
    return this.projectFacade.create(request).getId();
  }

  private EnvironmentDto environment(final UUID projectId, final String name, final boolean production) {
    final EnvironmentRequest request = new EnvironmentRequest();
    request.setProjectId(projectId);
    request.setName(name);
    request.setProduction(production);
    return this.environmentFacade.create(request);
  }

  private ComponentRequest componentRequest(final UUID projectId, final String name) {
    final ComponentRequest request = new ComponentRequest();
    request.setProjectId(projectId);
    request.setName(name);
    request.setSourceType(SourceType.DOCKER_HUB);
    request.setSourceRef("library/postgres");
    return request;
  }
}
