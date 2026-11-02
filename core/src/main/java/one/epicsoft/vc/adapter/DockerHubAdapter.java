package one.epicsoft.vc.adapter;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.version.VersionCandidate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * Docker Hub, through the API the website itself uses.
 *
 * <p>Not the registry v2 API: that one answers with tag names and nothing else,
 * while this one carries the publication date and the digest, which is what the
 * date and digest strategies need. The price is that it is not a documented API,
 * so everything here treats the payload as something that may change without
 * notice — a missing field is an error, never a default.
 *
 * <p>The source reference is the repository, with the namespace: {@code
 * library/postgres} for an official image, {@code grafana/grafana} for the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerHubAdapter implements SourceAdapter {

  private static final String URL =
    "https://hub.docker.com/v2/repositories/%s/tags?page_size=100&ordering=last_updated&page=%d";

  /**
   * How many pages are read at most.
   *
   * <p>One page is not enough: a repository that rebuilds its images regularly
   * pushes an older major or a less common variant off the newest hundred, and
   * the check would then report that the rule matches nothing while the rule is
   * correct. Five hundred tags cover the repositories this tool watches, and the
   * cap is there so that a repository with tens of thousands of tags cannot turn
   * one check into a crawl.
   */
  private static final int MAX_PAGES = 5;

  /** Matches the page_size above; a shorter page is the last one. */
  private static final int PAGE_SIZE = 100;

  /** Its own, not the one the web layer uses: this reads a foreign payload. */
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final SourceClient sourceClient;

  @Override
  @Nonnull
  public SourceType sourceType() {
    return SourceType.DOCKER_HUB;
  }

  @Override
  @Nonnull
  public List<VersionCandidate> fetchCandidates(@Nonnull final ComponentEntity component) {
    if (!nonBlank(component.getSourceRef())) {
      throw new SourceException("Component has no source reference; expected a repository such as library/postgres");
    }
    final String repository = component.getSourceRef().trim();
    if (!repository.contains("/")) {
      // An official image is library/<name> on this API; without the namespace
      // the request answers 404 and the reason would not be obvious.
      throw new SourceException("Source reference '" + repository + "' needs a namespace, for example library/"
                                + repository);
    }
    final List<VersionCandidate> candidates = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      final String payload = this.sourceClient.get(URL.formatted(repository, page));
      final List<VersionCandidate> onPage = this.parse(payload);
      candidates.addAll(onPage);
      if (onPage.size() < PAGE_SIZE || !hasNextPage(payload)) {
        break;
      }
    }
    if (candidates.isEmpty()) {
      throw new SourceException("Docker Hub lists no tags for this repository");
    }
    return candidates;
  }

  /**
   * Reads the tag list out of the payload. Separate from the request so that it
   * can be tested against a recorded answer without a network.
   */
  @Nonnull
  public List<VersionCandidate> parse(@Nonnull final String payload) {
    final JsonNode root;
    try {
      root = JSON.readTree(payload);
    } catch (final Exception exception) {
      throw new SourceException("Docker Hub answered with something that is not JSON", exception);
    }
    final JsonNode results = root.path("results");
    if (!results.isArray()) {
      throw new SourceException("Docker Hub answer has no 'results' array; the API has changed");
    }

    final List<VersionCandidate> candidates = new ArrayList<>();
    for (final JsonNode entry : results) {
      final String name = entry.path("name").asString(null);
      if (!nonBlank(name)) {
        throw new SourceException("Docker Hub returned a tag without a name; the API has changed");
      }
      candidates.add(new VersionCandidate(name,
                                          parseInstant(entry.path("last_updated").asString(null)),
                                          entry.path("digest").asString(null),
                                          null));
    }
    log.debug("Docker Hub page returned {} tags", candidates.size());
    return candidates;
  }

  /**
   * Docker Hub answers a page beyond the last one with 404, so a full last page - 200 tags at 100 a page - must not
   * lead to another request. Without the field the page size alone decides.
   */
  private static boolean hasNextPage(@Nonnull final String payload) {
    final JsonNode next = JSON.readTree(payload).path("next");
    return next.isMissingNode() || (!next.isNull() && nonBlank(next.asString(null)));
  }

  /** A date the API did not send is null, not now — the strategy has to notice. */
  private static Instant parseInstant(final String value) {
    if (!nonBlank(value)) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (final DateTimeParseException exception) {
      throw new SourceException("Docker Hub sent an unreadable date: " + value, exception);
    }
  }
}
