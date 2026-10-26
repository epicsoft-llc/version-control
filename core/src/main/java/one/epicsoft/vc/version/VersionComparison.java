package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.epicsoft.vc.enums.VersionStrategy;
import org.semver4j.Semver;
import java.util.Objects;

/**
 * Whether one version is actually behind another.
 *
 * <p>String inequality is not the same question, and answering it that way
 * produces tasks nobody can ever clear: a moving tag never equals a version
 * number, and a component pinned to an older major is not "behind" the newer
 * line it deliberately left.
 *
 * <p>Where the two cannot be ordered at all — a tag nothing can parse — this
 * answers "behind" for anything that differs. Asking about an update that turns
 * out to be none costs a glance; missing one costs a version that nobody
 * notices for months.
 */
public final class VersionComparison {

  private VersionComparison() {
    // utility class
  }

  /**
   * True when {@code current} is older than {@code latest}.
   *
   * @param strategy decides how the two are read; Debian versions are ordered by
   *                 their own rules, everything else by semantic versioning
   */
  public static boolean isBehind(@Nullable final String current, @Nullable final String latest,
                                 @Nullable final VersionStrategy strategy) {
    if (Objects.isNull(current) || Objects.isNull(latest) || current.isBlank() || latest.isBlank()) {
      return false;
    }
    if (current.equals(latest)) {
      return false;
    }
    return VersionStrategy.DEB_VERSION.equals(strategy) ? behindByDebian(current, latest)
                                                        : behindBySemver(current, latest);
  }

  /**
   * True when two recorded versions are not the same release. Debian versions ignore the distribution a package
   * was built for; everything else compares as written.
   */
  public static boolean differs(@Nonnull final String first, @Nonnull final String second,
                                @Nullable final VersionStrategy strategy) {
    final boolean debian = VersionStrategy.DEB_VERSION.equals(strategy);
    final DebianVersion left = debian ? DebianVersion.parse(first) : null;
    final DebianVersion right = debian ? DebianVersion.parse(second) : null;
    return Objects.nonNull(left) && Objects.nonNull(right) ? !left.isSameRelease(right) : !first.equals(second);
  }

  private static boolean behindByDebian(final String current, final String latest) {
    final DebianVersion left = DebianVersion.parse(current);
    final DebianVersion right = DebianVersion.parse(latest);
    if (Objects.isNull(left) || Objects.isNull(right)) {
      return true;
    }
    return left.compareTo(right) < 0;
  }

  private static boolean behindBySemver(final String current, final String latest) {
    final Semver left = Semver.coerce(current);
    final Semver right = Semver.coerce(latest);
    if (Objects.isNull(left) || Objects.isNull(right)) {
      return true;
    }
    return left.compareTo(right) < 0;
  }
}
