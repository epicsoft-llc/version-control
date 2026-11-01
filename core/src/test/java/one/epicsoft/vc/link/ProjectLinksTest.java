package one.epicsoft.vc.link;

import one.epicsoft.vc.data.FoundLink;
import one.epicsoft.vc.enums.LinkKind;
import one.epicsoft.vc.enums.SourceType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectLinksTest {

  @Test
  void derivesTheSourcePageEndOfLifeAndUpgradeGuideFromTheFields() {
    assertEquals(List.of(new FoundLink("https://hub.docker.com/_/postgres", LinkKind.IMAGE),
                         new FoundLink("https://endoflife.date/postgresql", LinkKind.END_OF_LIFE),
                         new FoundLink("https://example.com/upgrade", LinkKind.DOCUMENTATION)),
                 ProjectLinks.fromFields(SourceType.DOCKER_HUB, " library/postgres ", "postgresql", "https://example.com/upgrade"));
    assertEquals(List.of(new FoundLink("https://hub.docker.com/r/grafana/grafana", LinkKind.IMAGE)),
                 ProjectLinks.fromFields(SourceType.DOCKER_HUB, "grafana/grafana", null, null));
    assertEquals(List.of(new FoundLink("https://github.com/traefik/traefik", LinkKind.REPOSITORY)),
                 ProjectLinks.fromFields(SourceType.GITHUB_RELEASE, "traefik/traefik", "", null));
    assertEquals(List.of(new FoundLink("https://ghcr.io/acme/widget", LinkKind.IMAGE)),
                 ProjectLinks.fromFields(SourceType.GHCR, "ghcr.io/acme/widget", null, null));
    assertEquals(List.of(new FoundLink("https://download.example.com/linux/ubuntu", LinkKind.PACKAGE)),
                 ProjectLinks.fromFields(SourceType.APT_REPOSITORY, "https://download.example.com/linux/ubuntu", null, null));
  }

  @Test
  void derivesNothingFromAReferenceThatNamesNoPage() {
    assertEquals(List.of(), ProjectLinks.fromFields(SourceType.DOCKER_HUB, "postgres", null, "see the wiki"));
    assertEquals(List.of(), ProjectLinks.fromFields(SourceType.MANUAL, "anything", null, null));
    assertEquals(List.of(), ProjectLinks.fromFields(SourceType.GENERIC_REGISTRY, "registry.example.com/acme/widget", null, null));
  }

  @Test
  void turnsRepositoryAndImagePackageUrlsIntoLinks() {
    final Set<String> owners = Set.of("traefik");
    assertEquals(new FoundLink("https://github.com/traefik/traefik", LinkKind.REPOSITORY),
                 ProjectLinks.fromPackageUrl("pkg:github/traefik/traefik@v3.6.0", owners));
    assertEquals(new FoundLink("https://gitlab.com/acme/tools/widget", LinkKind.REPOSITORY),
                 ProjectLinks.fromPackageUrl("pkg:gitlab/acme/tools/widget", owners));
    assertEquals(new FoundLink("https://hub.docker.com/_/traefik", LinkKind.IMAGE),
                 ProjectLinks.fromPackageUrl("pkg:docker/library/traefik", owners));
    assertEquals(new FoundLink("https://hub.docker.com/r/traefik/whoami", LinkKind.IMAGE),
                 ProjectLinks.fromPackageUrl("pkg:docker/traefik/whoami", owners));
    assertEquals(new FoundLink("https://ghcr.io/traefik/traefik", LinkKind.IMAGE),
                 ProjectLinks.fromPackageUrl("pkg:docker/traefik/traefik?repository_url=ghcr.io", owners));
  }

  @Test
  void leavesOutImagesOfOtherPublishersAndPackagesWithoutAPage() {
    assertNull(ProjectLinks.fromPackageUrl("pkg:docker/bitnami/redis", Set.of("redis")));
    assertNull(ProjectLinks.fromPackageUrl("pkg:maven/org.keycloak/keycloak-core", Set.of()));
    assertNull(ProjectLinks.fromPackageUrl("pkg:deb/ubuntu/postgresql", Set.of()));
    assertNull(ProjectLinks.fromPackageUrl("pkg:docker/library/redis?repository_url=registry.example.com", Set.of()));
    assertNull(ProjectLinks.fromPackageUrl("cpe:2.3:a:postgresql:postgresql", Set.of()));
    assertNull(ProjectLinks.fromPackageUrl("pkg:github", Set.of()));
  }

  @Test
  void readsTheOwnerOfARepositoryPackageUrl() {
    assertEquals("grafana", ProjectLinks.ownerOf("pkg:github/Grafana/grafana"));
    assertNull(ProjectLinks.ownerOf("pkg:docker/library/postgres"));
  }

  @Test
  void findsTheRepositoriesAnImageDescriptionLinksTo() {
    final String description = """
        Maintained by [the Docker Community](https://github.com/docker-library/postgres).
        Issues: https://github.com/docker-library/postgres/issues — docs at https://github.com/docker-library/docs.
        Source of this description: https://github.com/docker-library/postgres/tree/master/17.
        Upstream: https://github.com/postgres/postgres. Sponsors: https://github.com/sponsors/docker-library.
        """;
    assertEquals(List.of(new FoundLink("https://github.com/docker-library/postgres", LinkKind.REPOSITORY)),
                 ProjectLinks.repositoriesIn(description, "library/postgres"));
  }

  @Test
  void takesNoSharedRepositoryOfTheOfficialImagesForTheProjectsOwn() {
    final String description = """
        https://github.com/docker-library/official-images, https://github.com/docker-library/official-images/pulls,
        https://github.com/docker-library/docs and https://github.com/traefik/traefik/issues.
        """;
    assertEquals(List.of(new FoundLink("https://github.com/traefik/traefik", LinkKind.REPOSITORY)),
                 ProjectLinks.repositoriesIn(description, "library/traefik"));
  }

  @Test
  void keepsTheMostLinkedRepositoryPerSite() {
    final String description = """
        Server: https://github.com/acme/widget-platform, https://github.com/acme/widget-platform/releases.
        Client library: https://github.com/acme/widget-js. Mirror: https://gitlab.com/acme-group/widget.git.
        Built on https://github.com/somebody/else.
        """;
    assertEquals(List.of(new FoundLink("https://github.com/acme/widget-platform", LinkKind.REPOSITORY),
                         new FoundLink("https://gitlab.com/acme-group/widget", LinkKind.REPOSITORY)),
                 ProjectLinks.repositoriesIn(description, "acme/widget"));
    assertEquals(List.of(), ProjectLinks.repositoriesIn(null, "acme/widget"));
  }

  @Test
  void readsRepositoriesBackFromAddresses() {
    assertEquals("traefik/traefik", ProjectLinks.gitHubRepositoryOf("https://github.com/traefik/traefik.git"));
    assertNull(ProjectLinks.gitHubRepositoryOf("https://github.com/orgs/acme/packages"));
    assertNull(ProjectLinks.gitHubRepositoryOf("https://gitlab.com/acme/widget"));
    assertEquals("library/postgres", ProjectLinks.dockerHubRepositoryOf("https://hub.docker.com/_/postgres"));
    assertEquals("grafana/grafana", ProjectLinks.dockerHubRepositoryOf("https://hub.docker.com/r/grafana/grafana/tags"));
    assertNull(ProjectLinks.dockerHubRepositoryOf("https://ghcr.io/acme/widget"));
  }

  @Test
  void judgesWhatALinkLeadsToByItsHost() {
    assertEquals(LinkKind.REPOSITORY, ProjectLinks.kindOf("https://www.github.com/acme/widget"));
    assertEquals(LinkKind.REPOSITORY, ProjectLinks.kindOf("https://codeberg.org/acme/widget"));
    assertEquals(LinkKind.IMAGE, ProjectLinks.kindOf("https://hub.docker.com/r/acme/widget"));
    assertEquals(LinkKind.END_OF_LIFE, ProjectLinks.kindOf("https://endoflife.date/widget"));
    assertEquals(LinkKind.OTHER, ProjectLinks.kindOf("https://example.com/widget"));
  }

  @Test
  void treatsOneAddressWrittenTwoWaysAsTheSame() {
    assertEquals(ProjectLinks.keyOf("https://github.com/Acme/Widget"), ProjectLinks.keyOf("http://www.github.com/acme/widget.git/"));
  }

  @Test
  void acceptsOnlyCompleteWebAddresses() {
    assertTrue(ProjectLinks.isWebAddress("https://example.com/path?query=1"));
    assertTrue(ProjectLinks.isWebAddress("http://example.com"));
    assertFalse(ProjectLinks.isWebAddress("javascript:alert(1)"));
    assertFalse(ProjectLinks.isWebAddress("ftp://example.com/file"));
    assertFalse(ProjectLinks.isWebAddress("example.com"));
    assertFalse(ProjectLinks.isWebAddress("https://example.com/with space"));
    assertFalse(ProjectLinks.isWebAddress(null));
  }
}
