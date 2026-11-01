package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.adapter.ProjectLinkAdapter;
import one.epicsoft.vc.adapter.SourceException;
import one.epicsoft.vc.data.FoundLink;
import one.epicsoft.vc.data.LinkSearchDraft;
import one.epicsoft.vc.data.LinkSearchOutcome;
import one.epicsoft.vc.dto.LinkSearchDto;
import one.epicsoft.vc.enums.LinkOrigin;
import one.epicsoft.vc.link.ProjectLinks;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/** Without a transaction: no database connection waits on a foreign server. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkSearchFacade {

  /** A product with a long list of images costs a handful of requests, not dozens. */
  private static final int MAX_DOCKER_HUB = 2;
  private static final int MAX_GITHUB = 3;

  private final ComponentLinkFacade componentLinkFacade;
  private final ProjectLinkAdapter projectLinkAdapter;

  public void search(@Nonnull final UUID componentId, final boolean manual) {
    final LinkSearchDraft draft = this.componentLinkFacade.collect(componentId, manual);
    this.componentLinkFacade.remember(draft, this.ask(draft));
  }

  @Nonnull
  public LinkSearchDto searchNow(@Nonnull final UUID componentId) {
    this.search(componentId, true);
    return this.componentLinkFacade.findByComponent(componentId);
  }

  @Nonnull
  private LinkSearchOutcome ask(@Nonnull final LinkSearchDraft draft) {
    final Map<LinkOrigin, List<FoundLink>> found = new EnumMap<>(LinkOrigin.class);
    final List<String> failures = new ArrayList<>();
    final Set<String> dockerHub = new LinkedHashSet<>();
    final Set<String> gitHub = new LinkedHashSet<>();
    ProjectLinks.fromFields(draft.sourceType(), draft.sourceRef(), null, null).forEach(link -> note(link, dockerHub, gitHub));

    if (nonBlank(draft.eolProduct())) {
      this.askEach(List.of(draft.eolProduct()), LinkOrigin.END_OF_LIFE, "endoflife.date", this.projectLinkAdapter::fromEndOfLife, found, failures)
          .forEach(link -> note(link, dockerHub, gitHub));
    }
    this.askEach(dockerHub.stream().limit(MAX_DOCKER_HUB).toList(), LinkOrigin.DOCKER_HUB, "Docker Hub", this.projectLinkAdapter::fromDockerHub,
                 found, failures)
        .forEach(link -> note(link, dockerHub, gitHub));
    this.askEach(gitHub.stream().limit(MAX_GITHUB).toList(), LinkOrigin.GITHUB, "GitHub", this.projectLinkAdapter::fromGitHub, found, failures);
    return new LinkSearchOutcome(found, failures);
  }

  /** Replaces an origin only if all answered and no site before failed: that one may have held back subjects. */
  @Nonnull
  private List<FoundLink> askEach(@Nonnull final List<String> subjects, @Nonnull final LinkOrigin origin, @Nonnull final String site,
                                  @Nonnull final Function<String, List<FoundLink>> request, @Nonnull final Map<LinkOrigin, List<FoundLink>> found,
                                  @Nonnull final List<String> failures) {
    final boolean earlierFailed = !failures.isEmpty();
    final List<FoundLink> links = new ArrayList<>();
    boolean answered = true;
    for (final String subject : subjects) {
      try {
        links.addAll(request.apply(subject));
      } catch (final SourceException exception) {
        log.warn("Link search at {} for {} failed: {}", site, subject, exception.getMessage());
        failures.add("%s (%s): %s".formatted(site, subject, exception.getMessage()));
        answered = false;
      }
    }
    if (answered && !earlierFailed) {
      found.put(origin, links);
    }
    return links;
  }

  private static void note(@Nonnull final FoundLink link, @Nonnull final Set<String> dockerHub, @Nonnull final Set<String> gitHub) {
    final String image = ProjectLinks.dockerHubRepositoryOf(link.url());
    if (nonNull(image)) {
      dockerHub.add(image);
    }
    final String repository = ProjectLinks.gitHubRepositoryOf(link.url());
    if (nonNull(repository)) {
      gitHub.add(repository);
    }
  }
}
