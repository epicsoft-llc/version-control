package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.epicsoft.vc.enums.VersionStrategy;
import org.semver4j.Semver;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  /** A suffix of digits alone is the build of a release, such as the 0045 of Zammad's 7.2.2-0045. */
  private static final Pattern BUILD = Pattern.compile("^([vV]?\\d+(?:\\.\\d+)*)-0*(\\d+)$");

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

  /**
   * The tag as a semantic version, with a suffix of digits alone kept as the build of its release. Coercing drops such
   * a suffix where it is not valid as written — leading zeros in 7.2.2-0045, two segments in 17.2-45 — and two builds of
   * one release then compare as equal, so the newer one is never proposed.
   */
  @Nullable
  static Semver semverOf(@Nonnull final String tag) {
    final Matcher build = BUILD.matcher(tag.trim());
    if (build.matches()) {
      final Semver release = Semver.coerce(build.group(1));
      return Objects.isNull(release) ? null : release.withPreRelease(build.group(2));
    }
    return Semver.coerce(tag);
  }

  private static boolean behindBySemver(final String current, final String latest) {
    final Semver left = semverOf(current);
    final Semver right = semverOf(latest);
    if (Objects.isNull(left) || Objects.isNull(right)) {
      return true;
    }
    return left.compareTo(right) < 0;
  }
}
