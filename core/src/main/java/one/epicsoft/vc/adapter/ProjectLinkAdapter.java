package one.epicsoft.vc.adapter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.data.FoundLink;
import one.epicsoft.vc.enums.LinkKind;
import one.epicsoft.vc.link.ProjectLinks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.isConfigured;

@Slf4j
@Component
public class ProjectLinkAdapter {

  private static final String END_OF_LIFE_URL = "https://endoflife.date/api/v1/products/%s";
  private static final String GITHUB_URL = "https://api.github.com/repos/%s";
  private static final String DOCKER_HUB_URL = "https://hub.docker.com/v2/repositories/%s/";

  /** Its own, not the one the web layer uses: this reads a foreign payload. */
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final SourceClient sourceClient;
  private final String token;

  public ProjectLinkAdapter(@Nonnull final SourceClient sourceClient, @Value("${vc.github.token:}") @Nullable final String token) {
    this.sourceClient = sourceClient;
    this.token = isConfigured(token) ? token : null;
  }

  @Nonnull
  public List<FoundLink> fromEndOfLife(@Nonnull final String product) {
    return this.parseEndOfLife(this.sourceClient.get(END_OF_LIFE_URL.formatted(product.trim())));
  }

  @Nonnull
  public List<FoundLink> parseEndOfLife(@Nonnull final String payload) {
    final JsonNode result = read(payload, "The end-of-life database").path("result");
    if (!result.isObject()) {
      throw new SourceException("The end-of-life database answered without 'result'; the API has changed");
    }
    final List<String> purls = new ArrayList<>();
    for (final JsonNode identifier : result.path("identifiers")) {
      if ("purl".equals(identifier.path("type").asString(null)) && nonNull(identifier.path("id").asString(null))) {
        purls.add(identifier.path("id").asString());
      }
    }
    final Set<String> owners = new HashSet<>();
    purls.stream().map(ProjectLinks::ownerOf).filter(Objects::nonNull).forEach(owners::add);

    final List<FoundLink> links = new ArrayList<>();
    purls.stream().map(purl -> ProjectLinks.fromPackageUrl(purl, owners)).filter(Objects::nonNull).forEach(links::add);
    final String policy = result.path("links").path("releasePolicy").asString(null);
    if (ProjectLinks.isWebAddress(policy)) {
      links.add(new FoundLink(policy, LinkKind.RELEASE_POLICY));
    }
    return links;
  }

  @Nonnull
  public List<FoundLink> fromGitHub(@Nonnull final String repository) {
    return this.parseGitHub(this.sourceClient.get(GITHUB_URL.formatted(repository), SourceClient.bearer(this.token)));
  }

  @Nonnull
  public List<FoundLink> parseGitHub(@Nonnull final String payload) {
    final JsonNode root = read(payload, "GitHub");
    if (!root.has("full_name")) {
      throw new SourceException("GitHub answered without 'full_name'; the API has changed");
    }
    final String homepage = root.path("homepage").asString(null);
    return ProjectLinks.isWebAddress(homepage) ? List.of(new FoundLink(homepage.trim(), LinkKind.HOMEPAGE)) : List.of();
  }

  @Nonnull
  public List<FoundLink> fromDockerHub(@Nonnull final String repository) {
    return this.parseDockerHub(this.sourceClient.get(DOCKER_HUB_URL.formatted(repository)), repository);
  }

  @Nonnull
  public List<FoundLink> parseDockerHub(@Nonnull final String payload, @Nonnull final String repository) {
    final JsonNode root = read(payload, "Docker Hub");
    if (!root.has("name")) {
      throw new SourceException("Docker Hub answered without 'name'; the API has changed");
    }
    return ProjectLinks.repositoriesIn(root.path("full_description").asString(null), repository);
  }

  @Nonnull
  private static JsonNode read(@Nonnull final String payload, @Nonnull final String site) {
    final JsonNode root;
    try {
      root = JSON.readTree(payload);
    } catch (final Exception exception) {
      throw new SourceException("%s answered with something that is not JSON".formatted(site), exception);
    }
    if (!root.isObject()) {
      throw new SourceException("%s answered with %s where an object was expected".formatted(site, root.getNodeType()));
    }
    return root;
  }
}
