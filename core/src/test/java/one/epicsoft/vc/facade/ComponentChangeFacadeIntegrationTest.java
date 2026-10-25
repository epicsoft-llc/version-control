package one.epicsoft.vc.facade;

import one.epicsoft.vc.AbstractIntegrationTest;
import one.epicsoft.vc.dto.ComponentChangeDto;
import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentEnvironmentDto;
import one.epicsoft.vc.dto.ComponentEnvironmentRequest;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.EnvironmentDto;
import one.epicsoft.vc.dto.EnvironmentRequest;
import one.epicsoft.vc.dto.ProjectRequest;
import one.epicsoft.vc.dto.RolloutPlanRequest;
import one.epicsoft.vc.dto.UpdateDecisionRequest;
import one.epicsoft.vc.dto.UpgradeStopRequest;
import one.epicsoft.vc.enums.DecisionState;
import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The log of what was changed on a component. Its value is in what it leaves out as much as in what it writes:
 * a save that changed nothing must leave no trace, or the log fills with noise and nobody reads it.
 */
@Transactional
class ComponentChangeFacadeIntegrationTest extends AbstractIntegrationTest {

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
  private ComponentChangeFacade componentChangeFacade;

  @Test
  void recordsTheCreationAndOnlyTheFieldsThatChanged() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.request(projectId, "Nexus"));

    final ComponentRequest update = this.request(projectId, "Nexus");
    update.setMaxStep(MaxStep.MAJOR);
    update.setDescription("Repository manager");
    this.componentFacade.update(component.getId(), update);

    final List<ComponentChangeDto> changes = this.componentChangeFacade.findAllByComponent(component.getId());
    assertEquals(3, changes.size());
    assertEquals(Set.of("created", "maxStep", "description"), changes.stream().map(ComponentChangeDto::getField).collect(Collectors.toSet()));
    final ComponentChangeDto maxStep = this.change(changes, "maxStep");
    assertEquals("MINOR", maxStep.getOldValue());
    assertEquals("MAJOR", maxStep.getNewValue());
    assertNull(this.change(changes, "description").getOldValue());
  }

  @Test
  void leavesNoTraceOfASaveThatChangedNothing() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.request(projectId, "Nexus"));

    this.componentFacade.update(component.getId(), this.request(projectId, "Nexus"));

    assertEquals(List.of("created"), this.componentChangeFacade.findAllByComponent(component.getId()).stream()
                                                               .map(ComponentChangeDto::getField).toList());
  }

  @Test
  void writesAListAsItsEntries() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.request(projectId, "Nexus"));
    final UpgradeStopRequest stop = new UpgradeStopRequest();
    stop.setVersion("3.70.0");
    final ComponentRequest update = this.request(projectId, "Nexus");
    update.setUpgradeStops(List.of(stop));

    this.componentFacade.update(component.getId(), update);

    final ComponentChangeDto stops = this.change(this.componentChangeFacade.findAllByComponent(component.getId()), "upgradeStops");
    assertNull(stops.getOldValue());
    assertEquals("3.70.0", stops.getNewValue());
  }

  @Test
  void recordsDecisionsAndRolloutPlansWithTheirNotes() {
    final UUID projectId = this.project();
    final ComponentDto component = this.componentFacade.create(this.request(projectId, "Nexus"));
    final UpdateDecisionRequest decision = new UpdateDecisionRequest();
    decision.setComponentId(component.getId());
    decision.setState(DecisionState.HELD);
    decision.setNote("Last free version");
    this.updateDecisionFacade.decide(decision);
    final ComponentEnvironmentDto cell = this.cell(projectId, component, "3.76.1");
    final RolloutPlanRequest plan = new RolloutPlanRequest();
    plan.setPlannedVersion("3.77.0");
    plan.setPlannedFor(LocalDate.of(2026, 11, 1));
    plan.setNote("Sunday window");
    this.componentEnvironmentFacade.plan(cell.getId(), plan);

    final List<ComponentChangeDto> changes = this.componentChangeFacade.findAllByComponent(component.getId());
    final ComponentChangeDto held = this.change(changes, ComponentChangeFacade.DECISION);
    assertNull(held.getOldValue());
    assertEquals("HELD", held.getNewValue());
    assertEquals("Last free version", held.getNote());
    final ComponentChangeDto planned = this.change(changes, ComponentChangeFacade.ROLLOUT_PLAN);
    assertEquals("Production: 3.77.0, 2026-11-01", planned.getNewValue());
    assertEquals("Sunday window", planned.getNote());
  }

  @Test
  void listsTheLatestChangesOfAProjectWithTheirComponents() {
    final UUID projectId = this.project();
    this.componentFacade.create(this.request(projectId, "Nexus"));
    this.componentFacade.create(this.request(projectId, "Vault"));
    // Another project's changes stay with that project.
    this.componentFacade.create(this.request(this.project(), "Traefik"));

    final List<ComponentChangeDto> changes = this.componentChangeFacade.findRecentByProject(projectId, 20);

    assertEquals(2, changes.size());
    assertTrue(changes.stream().map(ComponentChangeDto::getComponentName).toList().containsAll(List.of("Nexus", "Vault")));
    assertEquals(1, this.componentChangeFacade.findRecentByProject(projectId, 1).size());
  }

  private ComponentChangeDto change(final List<ComponentChangeDto> changes, final String field) {
    return changes.stream()
                  .filter(change -> field.equals(change.getField()))
                  .findFirst()
                  .orElseThrow(() -> new AssertionError("no change of %s in %s".formatted(field, changes)));
  }

  private ComponentEnvironmentDto cell(final UUID projectId, final ComponentDto component, final String version) {
    final EnvironmentRequest environmentRequest = new EnvironmentRequest();
    environmentRequest.setProjectId(projectId);
    environmentRequest.setName("Production");
    final EnvironmentDto environment = this.environmentFacade.create(environmentRequest);
    final ComponentEnvironmentRequest request = new ComponentEnvironmentRequest();
    request.setComponentId(component.getId());
    request.setEnvironmentId(environment.getId());
    request.setCurrentVersion(version);
    return this.componentEnvironmentFacade.createOrUpdate(request);
  }

  private UUID project() {
    final ProjectRequest request = new ProjectRequest();
    request.setName("Project %s".formatted(UUID.randomUUID()));
    return this.projectFacade.create(request).getId();
  }

  private ComponentRequest request(final UUID projectId, final String name) {
    final ComponentRequest request = new ComponentRequest();
    request.setProjectId(projectId);
    request.setName(name);
    request.setSourceType(SourceType.DOCKER_HUB);
    request.setSourceRef("sonatype/nexus3");
    return request;
  }
}
