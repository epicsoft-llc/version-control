package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.ComponentChangeDto;
import one.epicsoft.vc.enums.DecisionState;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentEntity;
import one.epicsoft.vc.jpa.entity.ComponentPackageEntity;
import one.epicsoft.vc.jpa.entity.UpgradeStopEntity;
import one.epicsoft.vc.mapper.ComponentChangeMapper;
import one.epicsoft.vc.mapper.ComponentMapper;
import one.epicsoft.vc.service.ComponentChangeService;
import one.epicsoft.vc.service.ComponentDependencyService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.ProjectService;
import one.epicsoft.vc.service.UpgradeStopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.isNull;
import static one.epicsoft.vc.util.VcUtil.nonEquals;

/**
 * What was changed on a component, written where the change happens and read back as its log.
 *
 * <p>The master data is compared as a snapshot before and after a save, so a field added to the component later
 * shows up here without a second list to keep in step.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentChangeFacade {

  public static final String CREATED = "created";
  public static final String DECISION = "decision";
  public static final String ROLLOUT_PLAN = "rolloutPlan";
  private static final String PACKAGES = "packages";
  private static final String UPGRADE_STOPS = "upgradeStops";
  private static final String DEPENDENCIES = "dependencies";
  /** Cannot change, so it never has anything to say. */
  private static final String PROJECT_ID = "projectId";

  private final ComponentService componentService;
  private final ProjectService projectService;
  private final ComponentChangeService componentChangeService;
  private final ComponentChangeMapper componentChangeMapper;
  private final ComponentMapper componentMapper;
  private final UpgradeStopService upgradeStopService;
  private final ComponentDependencyService componentDependencyService;
  private final JsonMapper jsonMapper;

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<ComponentChangeDto> findAllByComponent(@Nonnull final UUID componentId) {
    this.componentService.getById(componentId);
    return this.componentChangeMapper.toDtos(this.componentChangeService.findAllByComponent(componentId));
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<ComponentChangeDto> findRecentByProject(@Nonnull final UUID projectId, final int limit) {
    this.projectService.getById(projectId);
    return this.componentChangeMapper.toDtos(this.componentChangeService.findRecentByProject(projectId, limit));
  }

  /** The component as text, field by field and with its lists, the way the log compares it. */
  @Nonnull
  public Map<String, String> snapshot(@Nonnull final ComponentEntity component) {
    final Map<String, String> values = new LinkedHashMap<>();
    final JsonNode fields = this.jsonMapper.valueToTree(this.componentMapper.toRequest(component));
    fields.properties().forEach(field -> values.put(field.getKey(), text(field.getValue())));
    values.remove(PROJECT_ID);
    values.put(PACKAGES, joined(this.componentService.findPackages(component.getId()).stream()
                                    .sorted(Comparator.comparing(ComponentPackageEntity::getSortOrder))
                                    .map(entry -> entry.isLeadingPackage() ? "%s*".formatted(entry.getName()) : entry.getName())
                                    .toList()));
    values.put(UPGRADE_STOPS, joined(this.upgradeStopService.findAllByComponent(component.getId()).stream()
                                         .map(UpgradeStopEntity::getVersion)
                                         .toList()));
    values.put(DEPENDENCIES, joined(this.componentDependencyService.findAllByComponent(component.getId()).stream()
                                        .map(entry -> "%s %s".formatted(entry.getDependsOn().getName(), entry.getVersionRange()))
                                        .toList()));
    return values;
  }

  /** One entry per field whose text differs; a save that changed nothing leaves no trace. */
  @Transactional(rollbackFor = Throwable.class)
  public void recordDifferences(@Nonnull final ComponentEntity component,
                                @Nonnull final Map<String, String> before,
                                @Nonnull final Map<String, String> after) {
    final Instant now = Instant.now();
    final Set<String> fields = new LinkedHashSet<>(before.keySet());
    fields.addAll(after.keySet());
    fields.stream()
          .filter(field -> nonEquals(before.get(field), after.get(field)))
          .forEach(field -> this.componentChangeService.record(component, now, field, before.get(field), after.get(field), null));
  }

  @Transactional(rollbackFor = Throwable.class)
  public void recordCreated(@Nonnull final ComponentEntity component) {
    this.componentChangeService.record(component, Instant.now(), CREATED, null, component.getName(), null);
  }

  @Transactional(rollbackFor = Throwable.class)
  public void recordDecision(@Nonnull final ComponentEntity component,
                             @Nullable final DecisionState before,
                             @Nonnull final DecisionState after,
                             @Nullable final String note) {
    this.componentChangeService.record(component, Instant.now(), DECISION, isNull(before) ? null : before.name(), after.name(), note);
  }

  /** Called with the plan as it was, before the cell changes it. */
  @Transactional(rollbackFor = Throwable.class)
  public void recordPlan(@Nonnull final ComponentEnvironmentEntity cell, @Nullable final String before) {
    this.componentChangeService.record(cell.getComponent(), Instant.now(), ROLLOUT_PLAN, before, planText(cell), cell.getPlannedNote());
  }

  /** The plan of a cell as one line — environment, version, day — or null without one. */
  @Nullable
  public static String planText(@Nonnull final ComponentEnvironmentEntity cell) {
    return cell.hasPlan()
           ? "%s: %s, %s".formatted(cell.getEnvironment().getName(), cell.getPlannedVersion(), cell.getPlannedFor())
           : null;
  }

  @Nullable
  private static String text(@Nonnull final JsonNode value) {
    if (value.isNull()) {
      return null;
    }
    return value.isString() ? value.asString() : value.toString();
  }

  /** An empty list is no value at all, so the log reads "none → x" rather than "→ x". */
  @Nullable
  private static String joined(@Nonnull final List<String> entries) {
    return entries.isEmpty() ? null : String.join(", ", entries);
  }
}
