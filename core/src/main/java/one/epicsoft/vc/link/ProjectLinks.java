package one.epicsoft.vc.link;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.epicsoft.vc.data.FoundLink;
import one.epicsoft.vc.data.PackageUrl;
import one.epicsoft.vc.enums.LinkKind;
import one.epicsoft.vc.enums.SourceType;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/** Where a component lives, from addresses and identifiers alone — no request, so all of it is testable offline. */
public final class ProjectLinks {

  private static final String DOCKER_HUB = "https://hub.docker.com";
  private static final String OFFICIAL_NAMESPACE = "library";
  private static final Set<String> CODE_HOSTS = Set.of("github.com", "gitlab.com", "codeberg.org", "bitbucket.org");
  private static final Set<String> IMAGE_HOSTS = Set.of("hub.docker.com", "ghcr.io", "quay.io");
  private static final Set<String> REPOSITORY_TYPES = Set.of("github", "gitlab", "bitbucket");

  /** The name stops before a trailing dot, so the end of a sentence is not part of it. */
  private static final Pattern REPOSITORY = Pattern.compile(
      "https?://(?:www\\.)?(github\\.com|gitlab\\.com|codeberg\\.org|bitbucket\\.org)/([A-Za-z0-9_.-]+)/([A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*)");

  private static final Pattern DOCKER_HUB_PAGE =
      Pattern.compile("https?://hub\\.docker\\.com/(?:_/([A-Za-z0-9_.-]+)|r/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+))");

  private static final Set<String> NOT_OWNERS = Set.of("about", "apps", "collections", "customer-stories", "enterprise", "explore",
                                                       "features", "login", "marketplace", "notifications", "orgs", "pricing", "readme",
                                                       "search", "security", "settings", "site", "sponsors", "team", "topics", "users");

  private ProjectLinks() {
  }

  @Nonnull
  public static List<FoundLink> fromFields(@Nonnull final SourceType sourceType, @Nullable final String sourceRef,
                                           @Nullable final String eolProduct, @Nullable final String upgradeDocUrl) {
    final List<FoundLink> links = new ArrayList<>();
    if (nonBlank(sourceRef)) {
      final FoundLink source = sourceLink(sourceType, sourceRef.trim());
      if (nonNull(source)) {
        links.add(source);
      }
    }
    if (nonBlank(eolProduct)) {
      // An alias redirects to the product's page, so the configured name is enough.
      links.add(new FoundLink("https://endoflife.date/%s".formatted(eolProduct.trim()), LinkKind.END_OF_LIFE));
    }
    if (isWebAddress(upgradeDocUrl)) {
      links.add(new FoundLink(upgradeDocUrl.trim(), LinkKind.DOCUMENTATION));
    }
    return links;
  }

  @Nullable
  public static String dockerHubPage(@Nonnull final String repository) {
    final String[] parts = trimSlashes(repository).split("/");
    if (parts.length != 2 || !nonBlank(parts[0]) || !nonBlank(parts[1])) {
      return null;
    }
    return OFFICIAL_NAMESPACE.equals(parts[0]) ? "%s/_/%s".formatted(DOCKER_HUB, parts[1])
                                               : "%s/r/%s/%s".formatted(DOCKER_HUB, parts[0], parts[1]);
  }

  /** An image only if official or by an owner of the product's repositories: endoflife.date also lists images others build. */
  @Nullable
  public static FoundLink fromPackageUrl(@Nonnull final String purl, @Nonnull final Set<String> owners) {
    final PackageUrl parsed = PackageUrl.parse(purl);
    if (isNull(parsed)) {
      return null;
    }
    return switch (parsed.type()) {
      case "github" -> parsed.hasNamespace() ? repositoryLink("github.com", parsed.path()) : null;
      case "gitlab" -> parsed.hasNamespace() ? repositoryLink("gitlab.com", parsed.path()) : null;
      case "bitbucket" -> parsed.hasNamespace() ? repositoryLink("bitbucket.org", parsed.path()) : null;
      case "docker" -> imageLink(parsed, owners);
      default -> null;
    };
  }

  @Nullable
  public static String ownerOf(@Nonnull final String purl) {
    final PackageUrl parsed = PackageUrl.parse(purl);
    final boolean repository = nonNull(parsed) && REPOSITORY_TYPES.contains(parsed.type()) && parsed.hasNamespace();
    return repository ? parsed.segments().getFirst().toLowerCase(Locale.ROOT) : null;
  }

  /** Per site only the repository linked most often: a description also links tools, forums and what the image builds on. */
  @Nonnull
  public static List<FoundLink> repositoriesIn(@Nullable final String description, @Nonnull final String repository) {
    final String[] image = trimSlashes(repository).split("/");
    if (!nonBlank(description) || image.length != 2) {
      return List.of();
    }
    // An official image has no owner of its own: every description links docker-library's shared repositories.
    final boolean official = OFFICIAL_NAMESPACE.equals(image[0]);
    final Map<String, Map<String, Integer>> countsByHost = new LinkedHashMap<>();
    final Matcher matcher = REPOSITORY.matcher(description);
    while (matcher.find()) {
      final String host = matcher.group(1).toLowerCase(Locale.ROOT);
      final String owner = matcher.group(2);
      final String name = matcher.group(3).replaceFirst("(?i)\\.git$", "");
      final boolean belongs = compact(name).equals(compact(image[1])) || (!official && compact(owner).equals(compact(image[0])));
      if (belongs && !NOT_OWNERS.contains(owner.toLowerCase(Locale.ROOT))) {
        countsByHost.computeIfAbsent(host, _ -> new LinkedHashMap<>()).merge("%s/%s".formatted(owner, name), 1, Integer::sum);
      }
    }
    final List<FoundLink> links = new ArrayList<>();
    countsByHost.forEach((host, counts) -> counts.entrySet().stream()
                                                 .reduce((best, next) -> next.getValue() > best.getValue() ? next : best)
                                                 .ifPresent(best -> links.add(repositoryLink(host, best.getKey()))));
    return links;
  }

  @Nullable
  public static String gitHubRepositoryOf(@Nonnull final String url) {
    final Matcher matcher = REPOSITORY.matcher(url.trim());
    final boolean repository = matcher.lookingAt() && "github.com".equalsIgnoreCase(matcher.group(1))
                               && !NOT_OWNERS.contains(matcher.group(2).toLowerCase(Locale.ROOT));
    return repository ? "%s/%s".formatted(matcher.group(2), matcher.group(3).replaceFirst("(?i)\\.git$", "")) : null;
  }

  @Nullable
  public static String dockerHubRepositoryOf(@Nonnull final String url) {
    final Matcher matcher = DOCKER_HUB_PAGE.matcher(url.trim());
    if (!matcher.lookingAt()) {
      return null;
    }
    return nonNull(matcher.group(1)) ? "%s/%s".formatted(OFFICIAL_NAMESPACE, matcher.group(1))
                                     : "%s/%s".formatted(matcher.group(2), matcher.group(3));
  }

  @Nonnull
  public static LinkKind kindOf(@Nonnull final String url) {
    final String host = hostOf(url);
    if (CODE_HOSTS.contains(host)) {
      return LinkKind.REPOSITORY;
    }
    if (IMAGE_HOSTS.contains(host)) {
      return LinkKind.IMAGE;
    }
    return "endoflife.date".equals(host) ? LinkKind.END_OF_LIFE : LinkKind.OTHER;
  }

  /** Scheme, {@code www.}, case, a trailing slash and {@code .git} do not make another address. */
  @Nonnull
  public static String keyOf(@Nonnull final String url) {
    return url.trim()
              .toLowerCase(Locale.ROOT)
              .replaceFirst("^https?://", "")
              .replaceFirst("^www\\.", "")
              .replaceFirst("/+$", "")
              .replaceFirst("\\.git$", "");
  }

  public static boolean isWebAddress(@Nullable final String url) {
    if (!nonBlank(url) || url.trim().chars().anyMatch(Character::isWhitespace)) {
      return false;
    }
    try {
      final URI uri = new URI(url.trim());
      return nonNull(uri.getScheme()) && Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT)) && nonBlank(uri.getHost());
    } catch (final Exception exception) {
      return false;
    }
  }

  @Nullable
  private static FoundLink sourceLink(@Nonnull final SourceType sourceType, @Nonnull final String reference) {
    return switch (sourceType) {
      case DOCKER_HUB -> {
        final String page = dockerHubPage(reference);
        yield nonNull(page) ? new FoundLink(page, LinkKind.IMAGE) : null;
      }
      case GITHUB_RELEASE -> reference.contains("/") ? repositoryLink("github.com", trimSlashes(reference)) : null;
      case GHCR -> {
        // The address of an image on GHCR opens its package page in a browser.
        final String image = trimSlashes(reference.replaceFirst("(?i)^(https?://)?ghcr\\.io/", ""));
        yield image.contains("/") ? new FoundLink("https://ghcr.io/%s".formatted(image), LinkKind.IMAGE) : null;
      }
      case APT_REPOSITORY -> isWebAddress(reference) ? new FoundLink(reference, LinkKind.PACKAGE) : null;
      case GENERIC_REGISTRY, MANUAL -> null;
    };
  }

  @Nonnull
  private static String hostOf(@Nonnull final String url) {
    try {
      final String host = new URI(url.trim()).getHost();
      return nonBlank(host) ? host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "") : "";
    } catch (final Exception exception) {
      return "";
    }
  }

  @Nonnull
  private static FoundLink repositoryLink(@Nonnull final String host, @Nonnull final String path) {
    return new FoundLink("https://%s/%s".formatted(host, path), LinkKind.REPOSITORY);
  }

  @Nullable
  private static FoundLink imageLink(@Nonnull final PackageUrl parsed, @Nonnull final Set<String> owners) {
    final List<String> segments = parsed.segments();
    final String namespace = segments.size() > 1 ? segments.getFirst() : OFFICIAL_NAMESPACE;
    final String name = segments.getLast();
    final boolean wanted = segments.size() <= 2 && (OFFICIAL_NAMESPACE.equals(namespace) || owners.contains(namespace.toLowerCase(Locale.ROOT)));
    final String registry = parsed.qualifiers().getOrDefault("repository_url", "docker.io").toLowerCase(Locale.ROOT)
                                  .replaceFirst("^https?://", "").replaceFirst("/+$", "");
    if (!wanted) {
      return null;
    }
    return switch (registry) {
      case "docker.io", "hub.docker.com", "registry-1.docker.io", "index.docker.io" ->
          new FoundLink(dockerHubPage("%s/%s".formatted(namespace, name)), LinkKind.IMAGE);
      case "ghcr.io" -> new FoundLink("https://ghcr.io/%s/%s".formatted(namespace, name), LinkKind.IMAGE);
      case "quay.io" -> new FoundLink("https://quay.io/repository/%s/%s".formatted(namespace, name), LinkKind.IMAGE);
      default -> null;
    };
  }

  @Nonnull
  private static String trimSlashes(@Nonnull final String value) {
    return value.trim().replaceFirst("^/+", "").replaceFirst("/+$", "");
  }

  /** {@code umamisoftware} and {@code umami-software} are one owner. */
  @Nonnull
  private static String compact(@Nonnull final String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}
