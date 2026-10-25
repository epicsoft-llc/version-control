package one.epicsoft.vc.facade;

import one.epicsoft.vc.AbstractIntegrationTest;
import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentEnvironmentRequest;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.EnvironmentDto;
import one.epicsoft.vc.dto.EnvironmentRequest;
import one.epicsoft.vc.dto.ProjectRequest;
import one.epicsoft.vc.dto.ReleaseLinkDto;
import one.epicsoft.vc.dto.UpgradeProposalDto;
import one.epicsoft.vc.enums.CheckState;
import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.jpa.entity.UpdateCheckEntity;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.UpdateCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The versions between where an environment stands and where it may go, with what to read about each. */
@Transactional
class UpgradeProposalFacadeIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private ProjectFacade projectFacade;

  @Autowired
  private EnvironmentFacade environmentFacade;

  @Autowired
  private ComponentFacade componentFacade;

  @Autowired
  private ComponentEnvironmentFacade componentEnvironmentFacade;

  @Autowired
  private UpgradeProposalFacade upgradeProposalFacade;

  @Autowired
  private UpdateCheckService updateCheckService;

  @Autowired
  private ComponentService componentService;

  @Test
  void listsTheVersionsUpToTheTargetOldestFirstWithTheirReleases() {
    final ComponentRequest request = this.request(SourceType.GITHUB_RELEASE, "traefik/traefik");
    request.setMaxStep(MaxStep.MINOR);
    final ComponentDto component = this.componentFacade.create(request);
    this.cell(component, "v3.1.0");
    this.check(component, List.of("v4.0.0", "v3.2.0", "v3.1.2", "v3.1.0"));

    final List<ReleaseLinkDto> releases = this.proposal(component).getReleases();

    // Not the running one, nothing past the target, and in the order they are read.
    assertEquals(List.of("v3.1.2", "v3.2.0"), releases.stream().map(ReleaseLinkDto::getVersion).toList());
    assertEquals("https://github.com/traefik/traefik/releases/tag/v3.1.2", releases.getFirst().getUrl());
  }

  @Test
  void buildsTheLinkFromTheTemplateOfTheComponent() {
    final ComponentRequest request = this.request(SourceType.DOCKER_HUB, "library/traefik");
    request.setReleaseNotesUrl("https://example.com/releases/v{version}");
    final ComponentDto component = this.componentFacade.create(request);
    this.cell(component, "3.1.0");
    this.check(component, List.of("3.1.2", "3.1.0"));

    assertEquals("https://example.com/releases/v3.1.2", this.proposal(component).getReleases().getFirst().getUrl());
  }

  @Test
  void namesTheVersionsEvenWithoutAPlaceForTheirNotes() {
    final ComponentDto component = this.componentFacade.create(this.request(SourceType.DOCKER_HUB, "library/postgres"));
    this.cell(component, "17.2");
    this.check(component, List.of("17.4", "17.3", "17.2"));

    final List<ReleaseLinkDto> releases = this.proposal(component).getReleases();

    assertEquals(List.of("17.3", "17.4"), releases.stream().map(ReleaseLinkDto::getVersion).toList());
    assertNull(releases.getFirst().getUrl());
  }

  private UpgradeProposalDto proposal(final ComponentDto component) {
    return this.upgradeProposalFacade.getByComponent(component.getId()).getFirst();
  }

  /** Writes the result of a check directly; the adapters are tested without a network elsewhere. */
  private void check(final ComponentDto component, final List<String> candidates) {
    final UpdateCheckEntity check = new UpdateCheckEntity();
    check.setComponent(this.componentService.getById(component.getId()));
    check.setCheckState(CheckState.OK);
    check.setLatestVersion(candidates.getFirst());
    check.setCheckedAt(Instant.now());
    check.setLastSuccessfulCheckAt(Instant.now());
    check.setCandidateTags(String.join("\n", candidates));
    this.updateCheckService.save(check);
  }

  private void cell(final ComponentDto component, final String version) {
    final EnvironmentRequest environmentRequest = new EnvironmentRequest();
    environmentRequest.setProjectId(component.getProjectId());
    environmentRequest.setName("Production");
    final EnvironmentDto environment = this.environmentFacade.create(environmentRequest);
    final ComponentEnvironmentRequest request = new ComponentEnvironmentRequest();
    request.setComponentId(component.getId());
    request.setEnvironmentId(environment.getId());
    request.setCurrentVersion(version);
    this.componentEnvironmentFacade.createOrUpdate(request);
  }

  private ComponentRequest request(final SourceType sourceType, final String sourceRef) {
    final ProjectRequest project = new ProjectRequest();
    project.setName("Project %s".formatted(UUID.randomUUID()));
    final ComponentRequest request = new ComponentRequest();
    request.setProjectId(this.projectFacade.create(project).getId());
    request.setName("Component");
    request.setSourceType(sourceType);
    request.setSourceRef(sourceRef);
    return request;
  }
}
