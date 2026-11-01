package one.epicsoft.vc.facade;

import one.epicsoft.vc.AbstractIntegrationTest;
import one.epicsoft.vc.data.FoundLink;
import one.epicsoft.vc.data.LinkSearchDraft;
import one.epicsoft.vc.data.LinkSearchOutcome;
import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentLinkDto;
import one.epicsoft.vc.dto.ComponentLinkRequest;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.LinkSearchDto;
import one.epicsoft.vc.dto.ProjectRequest;
import one.epicsoft.vc.enums.CheckState;
import one.epicsoft.vc.enums.LinkKind;
import one.epicsoft.vc.enums.LinkOrigin;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.exception.AppValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkSearchIntegrationTest extends AbstractIntegrationTest {

  private static final FoundLink REPOSITORY = new FoundLink("https://github.com/acme/widget", LinkKind.REPOSITORY);
  private static final FoundLink POLICY = new FoundLink("https://acme.example.com/support", LinkKind.RELEASE_POLICY);
  private static final FoundLink HOMEPAGE = new FoundLink("https://acme.example.com", LinkKind.HOMEPAGE);

  @Autowired
  private ProjectFacade projectFacade;

  @Autowired
  private ComponentFacade componentFacade;

  @Autowired
  private ComponentLinkFacade componentLinkFacade;

  @Autowired
  private JsonMapper jsonMapper;

  @Test
  void keepsLinksByHandAndShowsThemWithTheDerivedOnesOnce() {
    final ComponentDto component = this.create(List.of("https://wiki.example.com/widget", "https://hub.docker.com/r/acme/widget/"));

    assertEquals(List.of("https://wiki.example.com/widget", "https://hub.docker.com/r/acme/widget/"),
                 component.getLinks().stream().map(ComponentLinkDto::getUrl).toList());
    assertEquals(List.of(LinkKind.OTHER, LinkKind.IMAGE), component.getLinks().stream().map(ComponentLinkDto::getKind).toList());

    final LinkSearchDto links = this.componentLinkFacade.findByComponent(component.getId());
    // The derived page of the source is the same address with a slash, so it is shown once, as kept.
    assertEquals(List.of("https://hub.docker.com/r/acme/widget/", "https://endoflife.date/widget", "https://wiki.example.com/widget"),
                 links.getLinks().stream().map(ComponentLinkDto::getUrl).toList());
    assertEquals(List.of(LinkOrigin.MANUAL, LinkOrigin.SOURCE, LinkOrigin.MANUAL),
                 links.getLinks().stream().map(ComponentLinkDto::getOrigin).toList());
    assertNull(links.getCheckState());
  }

  @Test
  void refusesALinkThatOpensNothingOrIsListedTwice() {
    final AppValidationException invalid =
        assertThrows(AppValidationException.class, () -> this.create(List.of("javascript:alert(1)")));
    assertEquals("error.invalidLink", invalid.getMessageKey());

    final AppValidationException duplicate =
        assertThrows(AppValidationException.class, () -> this.create(List.of("https://example.com/a", "http://www.example.com/a/")));
    assertEquals("error.duplicateLink", duplicate.getMessageKey());
  }

  @Test
  void clearsTheLinksKeptByHandWithANullInAPatch() {
    final ComponentDto component = this.create(List.of("https://wiki.example.com/widget"));

    final ComponentDto patched = this.componentFacade.patch(component.getId(), this.jsonMapper.readTree("{\"links\": null}"));

    assertEquals(List.of(), patched.getLinks());
  }

  @Test
  void storesWhatTheSearchFoundAndKeepsTheLinksOfASiteThatFailed() {
    final ComponentDto component = this.create(List.of());
    final LinkSearchDraft draft = this.componentLinkFacade.collect(component.getId(), false);

    this.componentLinkFacade.remember(draft, new LinkSearchOutcome(Map.of(LinkOrigin.END_OF_LIFE, List.of(REPOSITORY, POLICY),
                                                                          LinkOrigin.GITHUB, List.of(HOMEPAGE)), List.of()));
    final LinkSearchDto first = this.componentLinkFacade.findByComponent(component.getId());
    assertEquals(CheckState.OK, first.getCheckState());
    assertEquals(List.of(LinkKind.REPOSITORY, LinkKind.IMAGE, LinkKind.HOMEPAGE, LinkKind.RELEASE_POLICY, LinkKind.END_OF_LIFE),
                 first.getLinks().stream().map(ComponentLinkDto::getKind).toList());

    // endoflife.date did not answer: its links stay, GitHub's are replaced, and the search says why.
    this.componentLinkFacade.remember(draft, new LinkSearchOutcome(Map.of(LinkOrigin.GITHUB, List.of()),
                                                                   List.of("endoflife.date (widget): timeout")));
    final LinkSearchDto second = this.componentLinkFacade.findByComponent(component.getId());
    assertEquals(CheckState.ERROR, second.getCheckState());
    assertEquals("endoflife.date (widget): timeout", second.getErrorMessage());
    final List<String> urls = second.getLinks().stream().map(ComponentLinkDto::getUrl).toList();
    assertTrue(urls.contains(REPOSITORY.url()) && urls.contains(POLICY.url()));
    assertTrue(!urls.contains(HOMEPAGE.url()));
  }

  @Test
  void dropsWhatWasFoundForASourceThatChanged() {
    final ComponentDto component = this.create(List.of());
    final LinkSearchDraft draft = this.componentLinkFacade.collect(component.getId(), false);
    this.componentLinkFacade.remember(draft, new LinkSearchOutcome(Map.of(LinkOrigin.GITHUB, List.of(HOMEPAGE)), List.of()));

    final ComponentRequest request = this.request(component.getProjectId(), List.of());
    request.setName(component.getName());
    request.setSourceRef("acme/gadget");
    this.componentFacade.update(component.getId(), request);

    final LinkSearchDto links = this.componentLinkFacade.findByComponent(component.getId());
    assertNull(links.getCheckState());
    assertTrue(links.getLinks().stream().noneMatch(link -> HOMEPAGE.url().equals(link.getUrl())));
    // A search that started before the change brings back the old source's links; they are not stored.
    this.componentLinkFacade.remember(draft, new LinkSearchOutcome(Map.of(LinkOrigin.GITHUB, List.of(HOMEPAGE)), List.of()));
    assertNull(this.componentLinkFacade.findByComponent(component.getId()).getCheckState());
  }

  @Test
  void refusesASearchByHandInsideTheMinimumIntervalUnlessTheLastOneFailed() {
    final ComponentDto component = this.create(List.of());
    final LinkSearchDraft draft = this.componentLinkFacade.collect(component.getId(), true);

    this.componentLinkFacade.remember(draft, new LinkSearchOutcome(Map.of(), List.of("Docker Hub (acme/widget): 503")));
    this.componentLinkFacade.collect(component.getId(), true);

    this.componentLinkFacade.remember(draft, new LinkSearchOutcome(Map.of(), List.of()));
    final AppValidationException refused =
        assertThrows(AppValidationException.class, () -> this.componentLinkFacade.collect(component.getId(), true));
    assertEquals("error.checkTooSoon", refused.getMessageKey());
    this.componentLinkFacade.collect(component.getId(), false);
  }

  private ComponentDto create(final List<String> links) {
    final ProjectRequest project = new ProjectRequest();
    project.setName("Project " + UUID.randomUUID());
    return this.componentFacade.create(this.request(this.projectFacade.create(project).getId(), links));
  }

  private ComponentRequest request(final UUID projectId, final List<String> links) {
    final ComponentRequest request = new ComponentRequest();
    request.setProjectId(projectId);
    request.setName("Widget " + UUID.randomUUID());
    request.setSourceType(SourceType.DOCKER_HUB);
    request.setSourceRef("acme/widget");
    request.setEolProduct("widget");
    request.setLinks(links.stream().map(url -> {
      final ComponentLinkRequest link = new ComponentLinkRequest();
      link.setUrl(url);
      return link;
    }).toList());
    return request;
  }
}
