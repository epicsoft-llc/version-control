package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.config.CheckProperties;
import one.epicsoft.vc.config.EolProperties;
import one.epicsoft.vc.dto.DependencyCheckDto;
import one.epicsoft.vc.dto.TaskEntryDto;
import one.epicsoft.vc.dto.TaskListDto;
import one.epicsoft.vc.dto.TaskSectionDto;
import one.epicsoft.vc.enums.CheckState;
import one.epicsoft.vc.enums.ConstraintState;
import one.epicsoft.vc.enums.DecisionState;
import one.epicsoft.vc.enums.TaskSection;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentEntity;
import one.epicsoft.vc.jpa.entity.EolStatusEntity;
import one.epicsoft.vc.jpa.entity.UpdateCheckEntity;
import one.epicsoft.vc.jpa.entity.UpdateDecisionEntity;
import one.epicsoft.vc.service.ComponentEnvironmentService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.EolStatusService;
import one.epicsoft.vc.service.ProjectService;
import one.epicsoft.vc.service.UpdateCheckService;
import one.epicsoft.vc.service.UpdateDecisionService;
import one.epicsoft.vc.version.AllowedCandidates;
import one.epicsoft.vc.version.UpgradePathCalculator;
import one.epicsoft.vc.version.UpgradeTarget;
import one.epicsoft.vc.version.VersionComparison;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * What has to be done in one project.
 *
 * <p>Assembled from what the checks found and what somebody decided about it.
 * Two rules shape every section:
 *
 * <ul>
 *   <li>A failing check reaches the list only after the grace period. A single
 *       network hiccup is not a task, and a list that fills up with them stops
 *       being read.</li>
 *   <li>A decision covers the version it was made about and nothing newer. That
 *       is checked when a version arrives, not here — the list reports what the
 *       decision says, and the decision is reopened at the source.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskListFacade {

  private final ProjectService projectService;
  private final ComponentService componentService;
  private final ComponentEnvironmentService componentEnvironmentService;
  private final UpdateCheckService updateCheckService;
  private final UpdateDecisionService updateDecisionService;
  private final EolStatusService eolStatusService;
  private final CheckProperties checkProperties;
  private final EolProperties eolProperties;
  private final UpgradePathCalculator upgradePathCalculator;
  private final AllowedCandidates allowedCandidates;
  private final DependencyFacade dependencyFacade;

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public TaskListDto getByProject(@Nonnull final UUID projectId) {
    // Reading it turns an unknown id into a 404 rather than an empty list, which
    // would read as "nothing to do".
    this.projectService.getById(projectId);

    final List<ComponentEntity> components = this.componentService.findAllByProject(projectId);
    final Map<UUID, UpdateCheckEntity> checks =
        this.updateCheckService.findAllByProject(projectId).stream()
            .collect(Collectors.toMap(UpdateCheckEntity::getComponentId, Function.identity()));
    final Map<UUID, UpdateDecisionEntity> decisions =
        this.updateDecisionService.findAllByProject(projectId).stream()
            .collect(Collectors.toMap(UpdateDecisionEntity::getComponentId, Function.identity()));
    final Map<UUID, EolStatusEntity> eolStatuses =
        this.eolStatusService.findAllByProject(projectId).stream()
            .collect(Collectors.toMap(EolStatusEntity::getComponentId, Function.identity()));
    // Backwards: what is deployed right now, per environment. A violation here
    // means something is already running outside what its vendor supports.
    final Map<UUID, List<DependencyCheckDto>> violations =
        this.dependencyFacade.checkProject(projectId).stream()
            .filter(check -> ConstraintState.SATISFIED != check.getState())
            // A prerequisite that is not recorded in this environment at all is a
            // gap in the inventory, and the matrix already shows it as one.
            // Repeating it here would fill the list with entries nobody can act
            // on, and a list like that stops being read.
            .filter(check -> nonBlank(check.getActualVersion()))
            .collect(Collectors.groupingBy(DependencyCheckDto::getComponentId));
    // Read once for the whole project: the forward check is asked per component
    // and environment, and a query per question would be a hundred of them.
    final DependencyFacade.DependencyIndex index = this.dependencyFacade.index(projectId);
    final Map<UUID, List<ComponentEnvironmentEntity>> cells =
        this.componentEnvironmentService.findAllByProject(projectId).stream()
            .collect(Collectors.groupingBy(ComponentEnvironmentEntity::getComponentId));

    final Map<TaskSection, List<TaskEntryDto>> entries = new EnumMap<>(TaskSection.class);
    for (final ComponentEntity component : components) {
      final UpdateCheckEntity check = checks.get(component.getId());
      final UpdateDecisionEntity decision = decisions.get(component.getId());
      final List<ComponentEnvironmentEntity> componentCells = cells.getOrDefault(component.getId(), List.of());

      this.addEolTask(entries, component, eolStatuses.get(component.getId()));
      this.addViolatedDependencies(entries, component, violations);
      addRolloutPlans(entries, component, componentCells);
      this.addUpdateTask(entries, component, check, decision, componentCells, index);
      this.addCheckError(entries, component, check);
      addMovingTags(entries, component, componentCells);
    }

    final TaskListDto list = new TaskListDto();
    list.setProjectId(projectId);
    // The enum order is the reading order, and empty sections are left out
    // rather than shown empty.
    list.setSections(Arrays.stream(TaskSection.values())
                         .filter(section -> !entries.getOrDefault(section, List.of()).isEmpty())
                         .map(section -> section(section, entries.get(section)))
                         .toList());
    return list;
  }

  /**
   * The end of support, in one of two sections depending on how close it is.
   *
   * <p>Nothing here consults a decision. An end of support has a deadline; being
   * postponed is exactly what it cannot be, and a snooze that silenced it would
   * be the most expensive button in the application.
   */
  private void addEolTask(@Nonnull final Map<TaskSection, List<TaskEntryDto>> entries,
                          @Nonnull final ComponentEntity component,
                          @Nullable final EolStatusEntity status) {
    // Not tied to a successful lookup. The dates of the last good answer are
    // kept on failure on purpose, and a deadline that was known yesterday does
    // not stop existing because a request failed today — dropping the entry
    // would take an out-of-support component off the start page entirely.
    if (isNull(status)) {
      return;
    }
    final LocalDate end = status.getEolDate();
    final boolean over = status.isEol() || (nonNull(end) && !end.isAfter(LocalDate.now()));
    final boolean escalate = nonNull(end)
                             && end.isBefore(LocalDate.now().plusMonths(this.eolProperties.getEscalateMonths()));
    final boolean warn = nonNull(end)
                         && end.isBefore(LocalDate.now().plusMonths(this.eolProperties.getWarnMonths()));
    if (!over && !escalate && !warn) {
      return;
    }

    final TaskEntryDto entry = entry(component, null, null, null);
    entry.setDetail(nonNull(end) ? end.toString() : null);
    add(entries, over || escalate ? TaskSection.EOL_EXCEEDED : TaskSection.EOL_WARNING, entry);
  }

  /**
   * A dependency that is not met by what is deployed, or cannot be evaluated.
   *
   * <p>Unknown belongs here as much as violated: a version nothing can read is
   * the case nobody checked by hand either, and leaving it out would report
   * "everything fits" for the one combination nothing is known about.
   */
  private void addViolatedDependencies(@Nonnull final Map<TaskSection, List<TaskEntryDto>> entries,
                                       @Nonnull final ComponentEntity component,
                                       @Nonnull final Map<UUID, List<DependencyCheckDto>> violations) {
    for (final DependencyCheckDto violation : violations.getOrDefault(component.getId(), List.of())) {
      // With the environment named: the same dependency yields one result per
      // environment, and without it the list carries rows nobody can tell apart.
      // The version stays empty on purpose — the one in the check belongs to the
      // prerequisite, and every other section puts the row's own version there.
      final TaskEntryDto entry = entry(component, violation.getEnvironmentName(), null, null);
      // The check itself travels with the entry; what it reads like is the
      // interface's business, in the reader's language.
      entry.setConstraints(List.of(violation));
      add(entries, TaskSection.DEPENDENCY_VIOLATED, entry);
    }
  }

  /**
   * A rollout planned for one environment: overdue once its day has passed, otherwise announced at the end of
   * the list. Whatever was decided about the component, the plan is the more precise statement and is shown.
   */
  private static void addRolloutPlans(@Nonnull final Map<TaskSection, List<TaskEntryDto>> entries,
                                      @Nonnull final ComponentEntity component,
                                      @Nonnull final List<ComponentEnvironmentEntity> cells) {
    final LocalDate today = LocalDate.now();
    cells.stream()
         .filter(ComponentEnvironmentEntity::isActive)
         .filter(ComponentEnvironmentEntity::hasPlan)
         .sorted(Comparator.comparing((ComponentEnvironmentEntity cell) -> cell.getEnvironment().getSortOrder())
                           .thenComparing(cell -> cell.getEnvironment().getName()))
         .forEach(cell -> {
           final TaskEntryDto entry = entry(component, cell.getEnvironment().getName(), cell.getCurrentVersion(),
                                            cell.getPlannedVersion());
           entry.setDetail(cell.getPlannedNote());
           // Noon, not midnight: shown in the reader's time zone, midnight UTC is the evening before in America.
           entry.setSince(nonNull(cell.getPlannedFor()) ? cell.getPlannedFor().atTime(12, 0).toInstant(ZoneOffset.UTC) : null);
           final boolean overdue = nonNull(cell.getPlannedFor()) && cell.getPlannedFor().isBefore(today);
           add(entries, overdue ? TaskSection.ROLLOUT_OVERDUE : TaskSection.ROLLOUT_PLANNED, entry);
         });
  }

  /**
   * An available update, filed by what was decided about it: nothing yet, a
   * postponement that has run out, or a block that came from the dependency
   * check. A planned or skipped update is a closed question and stays off the
   * list until a newer version reopens it; a held one stays off for good. An
   * environment with a rollout plan is filed under its plan instead.
   */
  private void addUpdateTask(@Nonnull final Map<TaskSection, List<TaskEntryDto>> entries,
                             @Nonnull final ComponentEntity component,
                             @Nullable final UpdateCheckEntity check,
                             @Nullable final UpdateDecisionEntity decision,
                             @Nonnull final List<ComponentEnvironmentEntity> cells,
                             @Nonnull final DependencyFacade.DependencyIndex index) {
    // Not tied to a successful check: a failing one keeps the version the last
    // good one found, and a component that is two versions behind must not drop
    // off the list because the source happened to answer with a 503.
    if (isNull(check) || !nonBlank(check.getLatestVersion())) {
      return;
    }
    final TaskSection section = sectionOf(decision);
    if (isNull(section)) {
      return;
    }

    cells.stream()
         .filter(ComponentEnvironmentEntity::isActive)
         .filter(cell -> !cell.hasPlan())
         // A moving tag never equals a version number and would sit here for
         // good; it has a section of its own where it can actually be acted on.
         .filter(cell -> !cell.isMovingTag())
         .filter(cell -> VersionComparison.isBehind(cell.getCurrentVersion(), check.getLatestVersion(),
                                                    component.getVersionStrategy()))
         // One entry per environment that is behind, in a fixed order: production
         // first, then the configured column order. Reporting only one of them
         // would hide the others and change between two reloads.
         .sorted(Comparator.comparing((ComponentEnvironmentEntity cell) -> cell.getEnvironment().isProduction())
                           .reversed()
                           .thenComparing(cell -> cell.getEnvironment().getSortOrder())
                           .thenComparing(cell -> cell.getEnvironment().getName()))
         .forEach(cell -> {
           // What the reader is shown is the next permissible target, not the
           // newest version. The stored candidates are what the source offered,
           // unfiltered, so they go through the rule first — otherwise a
           // proposal names a variant or a prerelease that the check itself
           // would never have reported.
           final UpgradeTarget target = this.upgradePathCalculator.calculate(
               cell.getCurrentVersion(),
               this.allowedCandidates.of(component, check, cell.getCurrentVersion()),
               component.getMaxStep(),
               component.getVersionStrategy());
           if (!target.hasTarget()) {
             // Everything available is past the limit, or updates are switched
             // off: shown on the component, never demanded here.
             return;
           }
           // Forwards: would this target break something that needs the
           // component? Then it is blocked, not available — the start page must
           // not ask for an upgrade that the detail page says is impossible.
           final List<DependencyCheckDto> blockers =
               this.dependencyFacade.blockers(index, component.getId(), cell.getEnvironmentId(), target.target());

           final TaskEntryDto entry = entry(component, cell.getEnvironment().getName(), cell.getCurrentVersion(),
                                            target.target());
           // The note stays the note, and the blockers travel as data beside it:
           // a postponement whose note explains why must not lose that note the
           // moment something blocks it.
           entry.setDetail(nonNull(decision) ? decision.getNote() : null);
           entry.setConstraints(blockers);
           // The postponement date where there is one, otherwise how long the
           // update has been available. Blank exactly where there is most
           // history would be the wrong way round.
           entry.setSince(nonNull(decision) && nonNull(decision.getSnoozeUntil())
                              ? decision.getSnoozeUntil()
                              : check.getLastSuccessfulCheckAt());
           add(entries, blockers.isEmpty() ? section : TaskSection.BLOCKED, entry);
         });
  }

  @Nullable
  private static TaskSection sectionOf(@Nullable final UpdateDecisionEntity decision) {
    if (isNull(decision) || DecisionState.OPEN.equals(decision.getState())) {
      return TaskSection.UPDATE_OPEN;
    }
    if (decision.isSnoozeExpired()) {
      return TaskSection.SNOOZE_EXPIRED;
    }
    if (DecisionState.BLOCKED.equals(decision.getState())) {
      return TaskSection.BLOCKED;
    }
    // Planned and skipped are answers that come back with a newer version; held does not come back.
    return null;
  }

  /**
   * A check that has been failing longer than the grace period. Measured from
   * the last answer, not from the last attempt: three failed attempts in an hour
   * are one outage, and the question is how long the tool has been blind.
   */
  private void addCheckError(@Nonnull final Map<TaskSection, List<TaskEntryDto>> entries,
                             @Nonnull final ComponentEntity component,
                             @Nullable final UpdateCheckEntity check) {
    if (isNull(check) || !CheckState.ERROR.equals(check.getCheckState())) {
      return;
    }
    final Instant blindSince = Objects.requireNonNullElse(check.getLastSuccessfulCheckAt(), check.getCreated());
    if (blindSince.isAfter(Instant.now().minus(this.checkProperties.getErrorGraceDays(), ChronoUnit.DAYS))) {
      return;
    }
    final TaskEntryDto entry = entry(component, null, null, check.getLatestVersion());
    entry.setDetail(check.getErrorMessage());
    entry.setSince(blindSince);
    add(entries, TaskSection.CHECK_ERROR, entry);
  }

  /**
   * A moving tag is allowed anywhere, but in production it means the running
   * image can change without anybody recording it — which is the one thing this
   * inventory is supposed to notice.
   */
  private static void addMovingTags(@Nonnull final Map<TaskSection, List<TaskEntryDto>> entries,
                                    @Nonnull final ComponentEntity component,
                                    @Nonnull final List<ComponentEnvironmentEntity> cells) {
    cells.stream()
         .filter(ComponentEnvironmentEntity::isActive)
         .filter(ComponentEnvironmentEntity::isMovingTag)
         .filter(cell -> cell.getEnvironment().isProduction())
         .forEach(cell -> {
           final TaskEntryDto entry = entry(component, cell.getEnvironment().getName(), cell.getCurrentVersion(), null);
           entry.setSince(cell.getLastChangedAt());
           add(entries, TaskSection.MOVING_TAG_IN_PRODUCTION, entry);
         });
  }

  @Nonnull
  private static TaskEntryDto entry(@Nonnull final ComponentEntity component,
                                    @Nullable final String environmentName,
                                    @Nullable final String currentVersion,
                                    @Nullable final String latestVersion) {
    final TaskEntryDto entry = new TaskEntryDto();
    entry.setComponentId(component.getId());
    entry.setComponentName(component.getName());
    entry.setEnvironmentName(environmentName);
    entry.setCurrentVersion(currentVersion);
    entry.setLatestVersion(latestVersion);
    entry.setConstraints(List.of());
    return entry;
  }

  private static void add(@Nonnull final Map<TaskSection, List<TaskEntryDto>> entries,
                          @Nonnull final TaskSection section,
                          @Nonnull final TaskEntryDto entry) {
    entries.computeIfAbsent(section, key -> new ArrayList<>()).add(entry);
  }

  @Nonnull
  private static TaskSectionDto section(@Nonnull final TaskSection section, @Nonnull final List<TaskEntryDto> entries) {
    final TaskSectionDto dto = new TaskSectionDto();
    dto.setSection(section);
    dto.setEntries(entries);
    return dto;
  }
}
