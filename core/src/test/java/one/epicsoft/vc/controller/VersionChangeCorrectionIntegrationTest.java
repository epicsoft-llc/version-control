package one.epicsoft.vc.controller;

import one.epicsoft.vc.AbstractIntegrationTest;
import one.epicsoft.vc.dto.ComponentEnvironmentRequest;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.EnvironmentRequest;
import one.epicsoft.vc.dto.ProjectRequest;
import one.epicsoft.vc.dto.VersionChangeDto;
import one.epicsoft.vc.dto.VersionChangeRequest;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.facade.ComponentEnvironmentFacade;
import one.epicsoft.vc.facade.ComponentFacade;
import one.epicsoft.vc.facade.EnvironmentFacade;
import one.epicsoft.vc.facade.ProjectFacade;
import one.epicsoft.vc.facade.VersionChangeFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH on a history entry: the description of a change can be corrected, the change itself cannot.
 *
 * <p>Every test reads back what the patch must not touch — versions, time and the cell's version — because a
 * correction that rewrites them would answer 200 just the same.
 */
@Transactional
@AutoConfigureMockMvc
class VersionChangeCorrectionIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProjectFacade projectFacade;

  @Autowired
  private EnvironmentFacade environmentFacade;

  @Autowired
  private ComponentFacade componentFacade;

  @Autowired
  private ComponentEnvironmentFacade componentEnvironmentFacade;

  @Autowired
  private VersionChangeFacade versionChangeFacade;

  @Test
  void correctsTheFieldsItNamesAndKeepsTheRest() throws Exception {
    final VersionChangeDto change = this.recordedChange();

    this.applyPatch(change.getId(), "{\"backupTaken\":false,\"rollbackPath\":\"Pin back to 17.2; there is no backup\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.backupTaken").value(false))
        .andExpect(jsonPath("$.rollbackPath").value("Pin back to 17.2; there is no backup"))
        .andExpect(jsonPath("$.note").value("Ran the migration first"))
        .andExpect(jsonPath("$.backupRef").value("snapshot-4711"))
        .andExpect(jsonPath("$.downtimeMinutes").value(12))
        .andExpect(jsonPath("$.fromVersion").value("17.2"))
        .andExpect(jsonPath("$.toVersion").value("18.0"))
        .andExpect(jsonPath("$.correctedAt").exists());

    final VersionChangeDto stored = this.versionChangeFacade.getById(change.getId());
    assertEquals(change.getChangedAt(), stored.getChangedAt());
    assertEquals("Pin back to 17.2; there is no backup", stored.getRollbackPath());
  }

  @Test
  void acceptsTheMergePatchMediaType() throws Exception {
    final VersionChangeDto change = this.recordedChange();

    this.mockMvc.perform(patch("/api/v1/version-changes/{id}", change.getId())
                             .contentType(ComponentController.MERGE_PATCH_JSON)
                             .content("{\"issues\":\"The extension had to be reinstalled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issues").value("The extension had to be reinstalled"));
  }

  @Test
  void nullClearsAFieldAndUnticksTheBackup() throws Exception {
    final VersionChangeDto change = this.recordedChange();

    // Responses leave out what has no value, so a cleared field is a missing one.
    this.applyPatch(change.getId(), "{\"backupRef\":null,\"backupTaken\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.backupRef").doesNotExist())
        .andExpect(jsonPath("$.backupTaken").value(false))
        .andExpect(jsonPath("$.note").value("Ran the migration first"));
  }

  @Test
  void refusesToRewriteTheChangeItself() throws Exception {
    final VersionChangeDto change = this.recordedChange();

    this.applyPatch(change.getId(), "{\"toVersion\":\"19.0\"}").andExpect(status().isBadRequest());
    this.applyPatch(change.getId(), "{\"changedAt\":\"2020-01-01T00:00:00Z\"}").andExpect(status().isBadRequest());
    this.applyPatch(change.getId(), "{\"componentEnvironmentId\":\"%s\"}".formatted(UUID.randomUUID()))
        .andExpect(status().isBadRequest());

    final VersionChangeDto stored = this.versionChangeFacade.getById(change.getId());
    assertEquals("18.0", stored.getToVersion());
    assertEquals(change.getChangedAt(), stored.getChangedAt());
    assertEquals("18.0", this.componentEnvironmentFacade.getById(change.getComponentEnvironmentId()).getCurrentVersion());
    assertNull(stored.getCorrectedAt());
  }

  @Test
  void namesTheFieldOfAnInvalidValue() throws Exception {
    final VersionChangeDto change = this.recordedChange();

    this.applyPatch(change.getId(), "{\"downtimeMinutes\":-1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("downtimeMinutes"));
  }

  @Test
  void refusesABodyThatIsNotAnObject() throws Exception {
    final VersionChangeDto change = this.recordedChange();

    this.applyPatch(change.getId(), "[]").andExpect(status().isBadRequest());
  }

  @Test
  void answersNotFoundForAnUnknownEntry() throws Exception {
    this.applyPatch(UUID.randomUUID(), "{\"note\":\"Nobody recorded this\"}").andExpect(status().isNotFound());
  }

  @Test
  void anEmptyPatchLeavesTheEntryAsRecorded() throws Exception {
    final VersionChangeDto change = this.recordedChange();

    this.applyPatch(change.getId(), "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctedAt").doesNotExist())
        .andExpect(jsonPath("$.backupTaken").value(true));
  }

  private ResultActions applyPatch(final UUID id, final String body) throws Exception {
    return this.mockMvc.perform(patch("/api/v1/version-changes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private VersionChangeDto recordedChange() {
    final UUID projectId = this.project();
    final ComponentRequest componentRequest = new ComponentRequest();
    componentRequest.setProjectId(projectId);
    componentRequest.setName("PostgreSQL");
    componentRequest.setSourceType(SourceType.DOCKER_HUB);
    componentRequest.setSourceRef("library/postgres");
    final UUID componentId = this.componentFacade.create(componentRequest).getId();

    final EnvironmentRequest environmentRequest = new EnvironmentRequest();
    environmentRequest.setProjectId(projectId);
    environmentRequest.setName("Production");
    environmentRequest.setProduction(Boolean.TRUE);
    final UUID environmentId = this.environmentFacade.create(environmentRequest).getId();

    final ComponentEnvironmentRequest cellRequest = new ComponentEnvironmentRequest();
    cellRequest.setComponentId(componentId);
    cellRequest.setEnvironmentId(environmentId);
    cellRequest.setCurrentVersion("17.2");
    final UUID cellId = this.componentEnvironmentFacade.createOrUpdate(cellRequest).getId();

    final VersionChangeRequest request = new VersionChangeRequest();
    request.setComponentEnvironmentId(cellId);
    request.setToVersion("18.0");
    request.setNote("Ran the migration first");
    request.setBackupTaken(Boolean.TRUE);
    request.setBackupRef("snapshot-4711");
    request.setRollbackPath("Restore the snapshot, then start 17.2 again");
    request.setDowntimeMinutes(12);
    return this.versionChangeFacade.changeVersion(request);
  }

  private UUID project() {
    final ProjectRequest request = new ProjectRequest();
    request.setName("Project %s".formatted(UUID.randomUUID()));
    return this.projectFacade.create(request).getId();
  }
}
