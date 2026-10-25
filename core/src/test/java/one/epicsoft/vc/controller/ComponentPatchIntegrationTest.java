package one.epicsoft.vc.controller;

import one.epicsoft.vc.AbstractIntegrationTest;
import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.ProjectRequest;
import one.epicsoft.vc.dto.UpgradeStopRequest;
import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.facade.ComponentFacade;
import one.epicsoft.vc.facade.ProjectFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH on a component: a merge patch over the stored state that then takes the way of a replacement.
 *
 * <p>What can go wrong is quiet — a field the patch does not name loses its value, or a misspelt one answers
 * 200 for a change that never happened — so every test reads back what it did not touch as well.
 */
@Transactional
@AutoConfigureMockMvc
class ComponentPatchIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProjectFacade projectFacade;

  @Autowired
  private ComponentFacade componentFacade;

  @Test
  void changesTheFieldsItNamesAndKeepsTheRest() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.applyPatch(component.getId(), "{\"checkEnabled\":false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkEnabled").value(false))
        .andExpect(jsonPath("$.name").value("Nexus"))
        .andExpect(jsonPath("$.description").value("Repository manager"))
        .andExpect(jsonPath("$.sourceRef").value("sonatype/nexus3"))
        .andExpect(jsonPath("$.maxStep").value("MAJOR"))
        .andExpect(jsonPath("$.upgradeStops[0].version").value("3.70.0"));

    final ComponentDto stored = this.componentFacade.getById(component.getId());
    assertEquals(Boolean.FALSE, stored.getCheckEnabled());
    assertEquals(MaxStep.MAJOR, stored.getMaxStep());
  }

  @Test
  void acceptsTheMergePatchMediaType() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.mockMvc.perform(patch("/api/v1/components/{id}", component.getId())
                             .contentType(ComponentController.MERGE_PATCH_JSON)
                             .content("{\"description\":\"Kept on the running version\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Kept on the running version"));
  }

  @Test
  void nullClearsAFieldOrRestoresItsDefault() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    // Responses leave out what has no value, so a cleared description is a missing field.
    this.applyPatch(component.getId(), "{\"description\":null,\"maxStep\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist())
        .andExpect(jsonPath("$.maxStep").value("MINOR"))
        .andExpect(jsonPath("$.sourceRef").value("sonatype/nexus3"));
  }

  @Test
  void aListReplacesTheStoredOneAndNullClearsIt() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.applyPatch(component.getId(), "{\"upgradeStops\":[{\"version\":\"3.80.0\",\"note\":\"Database migration\"}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgradeStops.length()").value(1))
        .andExpect(jsonPath("$.upgradeStops[0].version").value("3.80.0"));

    // In a replacement a null list keeps the stored one; in a merge patch null removes.
    this.applyPatch(component.getId(), "{\"upgradeStops\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgradeStops.length()").value(0));
  }

  @Test
  void refusesAFieldTheComponentDoesNotHave() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.applyPatch(component.getId(), "{\"chekEnabled\":false}")
        .andExpect(status().isBadRequest());

    assertEquals(Boolean.TRUE, this.componentFacade.getById(component.getId()).getCheckEnabled());
  }

  @Test
  void namesTheFieldWhenARequiredOneIsCleared() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.applyPatch(component.getId(), "{\"name\":null}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("name"));
  }

  @Test
  void refusesAValueOfTheWrongType() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.applyPatch(component.getId(), "{\"maxStep\":\"HUGE\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void refusesABodyThatIsNotAnObject() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.applyPatch(component.getId(), "[]")
        .andExpect(status().isBadRequest());
  }

  @Test
  void keepsTheComponentInItsProject() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.applyPatch(component.getId(), "{\"projectId\":\"%s\"}".formatted(this.project()))
        .andExpect(status().isBadRequest());

    assertEquals(component.getProjectId(), this.componentFacade.getById(component.getId()).getProjectId());
  }

  @Test
  void refusesANameThatIsTaken() throws Exception {
    final UUID projectId = this.project();
    this.component(projectId, "Nexus");
    final ComponentDto other = this.component(projectId, "Vault");

    this.applyPatch(other.getId(), "{\"name\":\"Nexus\"}")
        .andExpect(status().isConflict());
  }

  @Test
  void answersNotFoundForAnUnknownComponent() throws Exception {
    this.applyPatch(UUID.randomUUID(), "{\"checkEnabled\":false}")
        .andExpect(status().isNotFound());
  }

  @Test
  void anEmptyPatchChangesNothing() throws Exception {
    final ComponentDto component = this.component(this.project(), "Nexus");

    this.applyPatch(component.getId(), "{}")
        .andExpect(status().isOk());

    final ComponentDto stored = this.componentFacade.getById(component.getId());
    assertEquals(component.getDescription(), stored.getDescription());
    assertEquals(component.getMaxStep(), stored.getMaxStep());
    assertTrue(stored.getCheckEnabled());
    assertEquals(1, stored.getUpgradeStops().size());
  }

  private ResultActions applyPatch(final UUID id, final String body) throws Exception {
    return this.mockMvc.perform(patch("/api/v1/components/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private UUID project() {
    final ProjectRequest request = new ProjectRequest();
    request.setName("Project %s".formatted(UUID.randomUUID()));
    return this.projectFacade.create(request).getId();
  }

  private ComponentDto component(final UUID projectId, final String name) {
    final UpgradeStopRequest stop = new UpgradeStopRequest();
    stop.setVersion("3.70.0");
    final ComponentRequest request = new ComponentRequest();
    request.setProjectId(projectId);
    request.setName(name);
    request.setDescription("Repository manager");
    request.setSourceType(SourceType.DOCKER_HUB);
    request.setSourceRef("sonatype/nexus3");
    request.setMaxStep(MaxStep.MAJOR);
    request.setUpgradeStops(List.of(stop));
    return this.componentFacade.create(request);
  }
}
