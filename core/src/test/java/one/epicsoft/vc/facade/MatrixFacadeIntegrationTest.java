package one.epicsoft.vc.facade;

import one.epicsoft.vc.AbstractIntegrationTest;
import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentEnvironmentRequest;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.EnvironmentDto;
import one.epicsoft.vc.dto.EnvironmentRequest;
import one.epicsoft.vc.dto.MatrixCellDto;
import one.epicsoft.vc.dto.MatrixDto;
import one.epicsoft.vc.dto.MatrixRowDto;
import one.epicsoft.vc.dto.ProjectRequest;
import one.epicsoft.vc.enums.MatrixCellState;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.enums.VersionStrategy;
import one.epicsoft.vc.exception.AppNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The overview, and above all the four states a cell can be in: the difference
 * between "not here on purpose" and "nobody wrote it down" is the whole point.
 */
@Transactional
class MatrixFacadeIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private ProjectFacade projectFacade;

  @Autowired
  private EnvironmentFacade environmentFacade;

  @Autowired
  private ComponentFacade componentFacade;

  @Autowired
  private ComponentEnvironmentFacade componentEnvironmentFacade;

  @Autowired
  private MatrixFacade matrixFacade;

  @Test
  void keepsTheColumnsInTheConfiguredOrder() {
    final UUID projectId = this.project();
    this.environment(projectId, "Production", 2, true);
    this.environment(projectId, "Test", 0, false);
    this.environment(projectId, "Staging", 1, false);

    final MatrixDto matrix = this.matrixFacade.getByProject(projectId);

    assertEquals(3, matrix.getEnvironments().size());
    assertEquals("Test", matrix.getEnvironments().getFirst().getName());
    assertEquals("Production", matrix.getEnvironments().getLast().getName());
  }

  @Test
  void tellsTheFourStatesOfACellApart() {
    final UUID projectId = this.project();
    final EnvironmentDto production = this.environment(projectId, "Production", 0, true);
    final EnvironmentDto staging = this.environment(projectId, "Staging", 1, false);
    final EnvironmentDto test = this.environment(projectId, "Test", 2, false);
    // A fourth column with no record at all.
    this.environment(projectId, "Sandbox", 3, false);
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));

    this.cell(component.getId(), production.getId(), "17.2", true);
    this.cell(component.getId(), staging.getId(), null, true);
    this.cell(component.getId(), test.getId(), null, false);

    final MatrixRowDto row = this.matrixFacade.getByProject(projectId).getRows().getFirst();

    assertEquals(MatrixCellState.RECORDED, row.getCells().get(0).getState());
    assertEquals(MatrixCellState.UNKNOWN, row.getCells().get(1).getState());
    assertEquals(MatrixCellState.INACTIVE, row.getCells().get(2).getState());
    assertEquals(MatrixCellState.NOT_RECORDED, row.getCells().get(3).getState());
    // Nothing to open where no record exists.
    assertNull(row.getCells().get(3).getComponentEnvironmentId());
  }

  @Test
  void marksTheEnvironmentsThatDifferFromProduction() {
    final UUID projectId = this.project();
    final EnvironmentDto production = this.environment(projectId, "Production", 0, true);
    final EnvironmentDto staging = this.environment(projectId, "Staging", 1, false);
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));

    this.cell(component.getId(), production.getId(), "17.2", true);
    this.cell(component.getId(), staging.getId(), "18.0", true);

    final MatrixRowDto row = this.matrixFacade.getByProject(projectId).getRows().getFirst();

    assertTrue(row.getDeviating());
    // What is live is the reference, so only the other column is highlighted.
    assertFalse(row.getCells().get(0).getDeviating());
    assertTrue(row.getCells().get(1).getDeviating());
  }

  @Test
  void staysQuietWhenEveryEnvironmentAgrees() {
    final UUID projectId = this.project();
    final EnvironmentDto production = this.environment(projectId, "Production", 0, true);
    final EnvironmentDto staging = this.environment(projectId, "Staging", 1, false);
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));

    this.cell(component.getId(), production.getId(), "17.2", true);
    this.cell(component.getId(), staging.getId(), "17.2", true);

    final MatrixRowDto row = this.matrixFacade.getByProject(projectId).getRows().getFirst();

    assertFalse(row.getDeviating());
    assertFalse(row.getCells().get(1).getDeviating());
  }

  @Test
  void readsADebianPackageForAnotherDistributionAsTheSameVersion() {
    final UUID projectId = this.project();
    final EnvironmentDto noble = this.environment(projectId, "Noble", 0, true);
    final EnvironmentDto resolute = this.environment(projectId, "Resolute", 1, false);
    final ComponentDto component = this.componentFacade.create(this.debianRequest(projectId));

    this.cell(component.getId(), noble.getId(), "5:29.9.2-1~ubuntu.24.04~noble", true);
    this.cell(component.getId(), resolute.getId(), "5:29.9.2-1~ubuntu.26.04~resolute", true);

    final MatrixRowDto row = this.matrixFacade.getByProject(projectId).getRows().getFirst();

    assertFalse(row.getDeviating());
    assertFalse(row.getCells().get(1).getDeviating());
  }

  @Test
  void stillMarksADebianPackageThatIsAnotherVersion() {
    final UUID projectId = this.project();
    final EnvironmentDto noble = this.environment(projectId, "Noble", 0, true);
    final EnvironmentDto resolute = this.environment(projectId, "Resolute", 1, false);
    final ComponentDto component = this.componentFacade.create(this.debianRequest(projectId));

    this.cell(component.getId(), noble.getId(), "5:29.9.2-1~ubuntu.24.04~noble", true);
    this.cell(component.getId(), resolute.getId(), "5:29.9.1-1~ubuntu.26.04~resolute", true);

    final MatrixRowDto row = this.matrixFacade.getByProject(projectId).getRows().getFirst();

    assertTrue(row.getDeviating());
    assertTrue(row.getCells().get(1).getDeviating());
  }

  @Test
  void flagsAMovingTag() {
    final UUID projectId = this.project();
    final EnvironmentDto production = this.environment(projectId, "Production", 0, true);
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));
    this.cell(component.getId(), production.getId(), "17-alpine", true);

    final MatrixCellDto cell = this.matrixFacade.getByProject(projectId).getRows().getFirst().getCells().getFirst();

    assertTrue(cell.getMovingTag());
  }

  @Test
  void showsNothingAtAllForACellThatWasSwitchedOff() {
    final UUID projectId = this.project();
    final EnvironmentDto production = this.environment(projectId, "Production", 0, true);
    final ComponentDto component = this.componentFacade.create(this.componentRequest(projectId, "PostgreSQL"));
    this.cell(component.getId(), production.getId(), "17-alpine", true);
    this.cell(component.getId(), production.getId(), null, false);

    final MatrixCellDto cell = this.matrixFacade.getByProject(projectId).getRows().getFirst().getCells().getFirst();

    assertEquals(MatrixCellState.INACTIVE, cell.getState());
    // Neutral means neutral: no version left over, and no warning to act on.
    assertNull(cell.getCurrentVersion());
    assertFalse(cell.getMovingTag());
  }

  @Test
  void refusesAProjectThatDoesNotExist() {
    // An empty matrix would look like a project without components.
    assertThrows(AppNotFoundException.class, () -> this.matrixFacade.getByProject(UUID.randomUUID()));
  }

  private void cell(final UUID componentId, final UUID environmentId, final String version, final boolean active) {
    final ComponentEnvironmentRequest request = new ComponentEnvironmentRequest();
    request.setComponentId(componentId);
    request.setEnvironmentId(environmentId);
    request.setActive(active);
    request.setCurrentVersion(version);
    this.componentEnvironmentFacade.createOrUpdate(request);
  }

  private UUID project() {
    final ProjectRequest request = new ProjectRequest();
    request.setName("Project " + UUID.randomUUID());
    return this.projectFacade.create(request).getId();
  }

  private EnvironmentDto environment(final UUID projectId, final String name, final int sortOrder, final boolean production) {
    final EnvironmentRequest request = new EnvironmentRequest();
    request.setProjectId(projectId);
    request.setName(name);
    request.setSortOrder(sortOrder);
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

  private ComponentRequest debianRequest(final UUID projectId) {
    final ComponentRequest request = this.componentRequest(projectId, "Docker");
    request.setVersionStrategy(VersionStrategy.DEB_VERSION);
    return request;
  }
}
