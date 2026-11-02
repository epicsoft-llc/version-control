package one.epicsoft.vc.adapter;

import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.version.VersionCandidate;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.isNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The adapters against recorded answers.
 *
 * <p>No network: the payloads are pasted in, shortened to the fields that are
 * read. What is being tested is the half that breaks when an upstream API
 * changes shape — and that it then fails loudly instead of returning a version
 * nobody can trace.
 */
class AdapterParsingTest {

  private final DockerHubAdapter dockerHub = new DockerHubAdapter(new SourceClient());
  private final GitHubReleaseAdapter gitHub = new GitHubReleaseAdapter(new SourceClient(), null);
  private final AptRepositoryAdapter apt = new AptRepositoryAdapter(new SourceClient());

  @Test
  void readsTagsDatesAndDigestsFromDockerHub() {
    final String payload = """
        {"count": 2, "results": [
          {"name": "17.2-alpine", "last_updated": "2026-01-05T10:00:00Z", "digest": "sha256:aaa"},
          {"name": "17.3-alpine", "last_updated": "2026-02-05T10:00:00Z", "digest": "sha256:bbb"}
        ]}
        """;

    final List<VersionCandidate> candidates = this.dockerHub.parse(payload);

    assertEquals(2, candidates.size());
    assertEquals("17.2-alpine", candidates.getFirst().tag());
    assertEquals(Instant.parse("2026-01-05T10:00:00Z"), candidates.getFirst().releasedAt());
    assertEquals("sha256:aaa", candidates.getFirst().digest());
  }

  @Test
  void reportsAChangedDockerHubPayloadInsteadOfGuessing() {
    // The API is undocumented. When it changes, the check has to say so.
    assertThrows(SourceException.class, () -> this.dockerHub.parse("{\"tags\": []}"));
    assertThrows(SourceException.class, () -> this.dockerHub.parse("{\"results\": [{\"digest\": \"sha256:a\"}]}"));
    assertThrows(SourceException.class, () -> this.dockerHub.parse("<html>rate limited</html>"));
  }

  @Test
  void readsReleasesFromGitHubAndLeavesDraftsOut() {
    final String payload = """
        [
          {"tag_name": "v2.1.0", "published_at": "2026-02-01T00:00:00Z", "draft": false},
          {"tag_name": "v2.2.0", "published_at": null, "draft": true}
        ]
        """;

    final List<VersionCandidate> candidates = this.gitHub.parseReleases(payload);

    assertEquals(1, candidates.size());
    assertEquals("v2.1.0", candidates.getFirst().tag());
  }

  @Test
  void readsTagsFromGitHubWithoutDates() {
    final List<VersionCandidate> candidates = this.gitHub.parseTags("[{\"name\": \"v1.4.0\"}]");

    assertEquals("v1.4.0", candidates.getFirst().tag());
    // No timestamps here, which is why only SemVer can order this source.
    assertNull(candidates.getFirst().releasedAt());
  }

  @Test
  void reportsAChangedGitHubPayload() {
    assertThrows(SourceException.class, () -> this.gitHub.parseReleases("{\"message\": \"Not Found\"}"));
    assertThrows(SourceException.class, () -> this.gitHub.parseTags("[{\"commit\": {}}]"));
  }

  @Test
  void readsEveryPackageVersionFromAnAptIndex() {
    final String payload = """
        Package: docker-ce
        Version: 5:29.7.1-1~ubuntu.24.04~noble
        Architecture: amd64
        Description: Docker
         a continuation line that belongs to the field above

        Package: containerd.io
        Version: 2.3.3-1

        Package: docker-ce
        Version: 5:29.7.2-1~ubuntu.24.04~noble
        """;

    final List<VersionCandidate> candidates = this.apt.parse(payload);

    // Every version is in the file, not only the newest, and both versions of
    // docker-ce have to survive parsing for the comparison to find the newer one.
    assertEquals(3, candidates.size());
    assertTrue(candidates.stream().anyMatch(candidate -> "containerd.io".equals(candidate.packageName())));
    assertEquals(2, candidates.stream().filter(c -> "docker-ce".equals(c.packageName())).count());
    // The last stanza has no blank line after it and still counts.
    assertTrue(candidates.stream().anyMatch(c -> "5:29.7.2-1~ubuntu.24.04~noble".equals(c.tag())));
  }

  @Test
  void readsAPageThatIsNotTheLastOne() {
    // parse() answers per page and no longer decides whether a repository is
    // empty; that call belongs to the loop that walks the pages.
    final String payload = "{\"results\": [{\"name\": \"1.0\"}]}";

    assertEquals(1, this.dockerHub.parse(payload).size());
    assertEquals(0, this.dockerHub.parse("{\"results\": []}").size());
  }

  @Test
  void reportsAnAptIndexThatHoldsNothing() {
    assertThrows(SourceException.class, () -> this.apt.parse("<html>404</html>"));
  }

  @Test
  void stopsAtAFullLastPageInsteadOfAskingForOneMore() {
    // 200 tags: the second page is full, and Docker Hub answers a third with 404.
    final RecordedPages pages = new RecordedPages(dockerHubPage(1, 100, true), dockerHubPage(2, 100, false));

    final List<VersionCandidate> candidates = new DockerHubAdapter(pages).fetchCandidates(component("notifuse/notifuse"));

    assertEquals(200, candidates.size());
    assertEquals(2, pages.requested.size());
  }

  @Test
  void followsDockerHubToTheNextPageAndStopsAtAShortOne() {
    final RecordedPages pages = new RecordedPages(dockerHubPage(1, 100, true), dockerHubPage(2, 30, false));

    final List<VersionCandidate> candidates = new DockerHubAdapter(pages).fetchCandidates(component("library/postgres"));

    assertEquals(130, candidates.size());
    assertTrue(pages.requested.getLast().endsWith("&page=2"));
  }

  @Test
  void keepsReadingFullPagesWhenDockerHubSendsNoNextField() {
    final RecordedPages pages = new RecordedPages(dockerHubPage(1, 100, null), dockerHubPage(2, 5, null));

    assertEquals(105, new DockerHubAdapter(pages).fetchCandidates(component("library/postgres")).size());
  }

  private static ComponentEntity component(final String sourceRef) {
    final ComponentEntity component = new ComponentEntity();
    component.setSourceRef(sourceRef);
    return component;
  }

  /** {@code next}: true names a following page, false sends null, null leaves the field out. */
  private static String dockerHubPage(final int page, final int tags, final Boolean next) {
    final String results = IntStream.range(0, tags)
                                    .mapToObj(i -> "{\"name\": \"%d.%d\", \"last_updated\": \"2026-01-01T00:00:00Z\"}".formatted(page, i))
                                    .collect(Collectors.joining(","));
    final String nextField = isNull(next) ? "" : "\"next\": %s, ".formatted(next ? "\"https://hub.docker.com/next\"" : "null");
    return "{%s\"results\": [%s]}".formatted(nextField, results);
  }

  /** Answers the pages in order and fails like Docker Hub for any page after them. */
  private static final class RecordedPages extends SourceClient {
    private final List<String> answers;
    private final List<String> requested = new ArrayList<>();

    private RecordedPages(final String... answers) {
      this.answers = List.of(answers);
    }

    @Override
    public String get(final String url) {
      this.requested.add(url);
      if (this.requested.size() > this.answers.size()) {
        throw new SourceException("Request to %s failed: 404 Not Found".formatted(url));
      }
      return this.answers.get(this.requested.size() - 1);
    }
  }
}
