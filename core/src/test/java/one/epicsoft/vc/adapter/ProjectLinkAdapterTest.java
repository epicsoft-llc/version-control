package one.epicsoft.vc.adapter;

import one.epicsoft.vc.data.FoundLink;
import one.epicsoft.vc.enums.LinkKind;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectLinkAdapterTest {

  private final ProjectLinkAdapter adapter = new ProjectLinkAdapter(new SourceClient(), null);

  @Test
  void readsRepositoriesImagesAndThePolicyFromEndOfLife() {
    final String payload = """
        {"schema_version": "1.2.0", "result": {"name": "traefik",
          "identifiers": [
            {"type": "repology", "id": "traefik"},
            {"type": "purl", "id": "pkg:github/traefik/traefik"},
            {"type": "purl", "id": "pkg:docker/library/traefik"},
            {"type": "purl", "id": "pkg:docker/bitnami/traefik"},
            {"type": "purl", "id": "pkg:brew/traefik"},
            {"type": "cpe", "id": "cpe:2.3:a:traefik:traefik"}],
          "links": {"html": "https://endoflife.date/traefik", "releasePolicy": "https://doc.traefik.io/traefik/deprecation/releases"}}}
        """;

    assertEquals(List.of(new FoundLink("https://github.com/traefik/traefik", LinkKind.REPOSITORY),
                         new FoundLink("https://hub.docker.com/_/traefik", LinkKind.IMAGE),
                         new FoundLink("https://doc.traefik.io/traefik/deprecation/releases", LinkKind.RELEASE_POLICY)),
                 this.adapter.parseEndOfLife(payload));
  }

  @Test
  void readsAProductWithoutIdentifiersOrPolicy() {
    assertEquals(List.of(), this.adapter.parseEndOfLife("{\"result\": {\"name\": \"widget\", \"links\": {\"releasePolicy\": null}}}"));
  }

  @Test
  void readsTheWebsiteOfAGitHubRepository() {
    assertEquals(List.of(new FoundLink("https://grafana.com", LinkKind.HOMEPAGE)),
                 this.adapter.parseGitHub("{\"full_name\": \"grafana/grafana\", \"homepage\": \"https://grafana.com\"}"));
    assertEquals(List.of(), this.adapter.parseGitHub("{\"full_name\": \"acme/widget\", \"homepage\": \"\"}"));
    assertEquals(List.of(), this.adapter.parseGitHub("{\"full_name\": \"acme/widget\", \"homepage\": null}"));
  }

  @Test
  void readsTheRepositoriesOfADockerHubDescription() {
    final String payload = """
        {"name": "postgres", "namespace": "library",
         "full_description": "Maintained by https://github.com/docker-library/postgres, see https://github.com/docker-library/docs"}
        """;

    assertEquals(List.of(new FoundLink("https://github.com/docker-library/postgres", LinkKind.REPOSITORY)),
                 this.adapter.parseDockerHub(payload, "library/postgres"));
  }

  @Test
  void reportsAChangedOrForeignAnswerInsteadOfGuessing() {
    assertThrows(SourceException.class, () -> this.adapter.parseEndOfLife("[{\"cycle\": \"17\"}]"));
    assertThrows(SourceException.class, () -> this.adapter.parseEndOfLife("{\"schema_version\": \"1.2.0\"}"));
    assertThrows(SourceException.class, () -> this.adapter.parseGitHub("{\"message\": \"API rate limit exceeded\"}"));
    assertThrows(SourceException.class, () -> this.adapter.parseDockerHub("{\"message\": \"object not found\"}", "acme/widget"));
    assertThrows(SourceException.class, () -> this.adapter.parseDockerHub("<html>rate limited</html>", "acme/widget"));
  }
}
