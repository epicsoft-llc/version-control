package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * Which tags of a source count as a candidate for one component.
 *
 * <p>Without a rule of its own a component gets one derived from the tag that is
 * actually deployed. That settles two questions at once, and both of them would
 * otherwise be settled wrongly by any fixed rule:
 *
 * <ul>
 *   <li><strong>The variant.</strong> Running {@code 17.2-alpine}, the candidate
 *       is {@code 17.3-alpine} and not {@code 17.3-bookworm}. Changing the
 *       variant is not an update, it is a different image.</li>
 *   <li><strong>The number of segments.</strong> {@code 17} , {@code 17.2} and
 *       {@code 17.2.1} are three different conventions, and a component that
 *       publishes all three would otherwise jump between them.</li>
 * </ul>
 *
 * <p>A tag with no digits at all — {@code latest}, {@code stable} — yields no
 * rule. That is deliberate: such a tag moves, and the only honest way to watch it
 * is the digest, so the caller reports the missing rule instead of inventing one.
 */
public final class TagRule {

  /**
   * An optional prefix, the version core, then whatever the publisher appends.
   *
   * <p>The prefix covers what sits in front of a version without being one: a
   * variant such as the {@code alpine-} of {@code alpine-v19.5.2}, the {@code v}
   * of a GitHub release tag, and the epoch of a Debian version. Without them
   * here, {@code v2.1.0} yields no rule at all, and the epoch of
   * {@code 5:29.7.2} would be read as the major.
   */
  private static final Pattern TAG = Pattern.compile("^((?:[A-Za-z][0-9A-Za-z]*-)+)?([vV]|\\d+:)?(\\d+(?:\\.\\d+)*)(.*)$");

  /** A channel names no release: {@code 2.4.0-latest} follows every patch of 2.4.0. */
  private static final Pattern CHANNEL = Pattern.compile("(?i)(?:^|[-.])(?:latest|stable|edge|nightly|mainline)(?:[-.]|$)");

  private static final Pattern DIGITS = Pattern.compile("\\d+");

  /** rc, alpha, beta, and the numbered forms of them. */
  private static final Pattern PRERELEASE =
    Pattern.compile("(?i).*[-.](rc|alpha|beta|preview|snapshot|dev|m)\\.?\\d*$");

  /**
   * A suffix that says which build of a release this is, not which release —
   * alpine, bookworm, jre, a Debian revision, a prerelease marker.
   */
  private static final Pattern VARIANT = Pattern.compile("^[-.][0-9A-Za-z][0-9A-Za-z.~+-]*$");

  private TagRule() {
    // utility class
  }

  /**
   * Builds the pattern a component without its own rule is checked against.
   *
   * @return null when the tag carries no version core to derive anything from
   */
  @Nullable
  public static String derivePattern(@Nullable final String currentTag) {
    if (Objects.isNull(currentTag) || currentTag.isBlank()) {
      return null;
    }
    final Matcher matcher = TAG.matcher(currentTag.trim());
    if (!matcher.matches()) {
      return null;
    }
    final int segments = matcher.group(3).split("\\.").length;
    final StringBuilder pattern = new StringBuilder("^");
    // The variant in front stays literal like the one behind: alpine-v19.6.0 is a candidate, ubuntu-v19.6.0 is not.
    appendLiteral(pattern, Objects.requireNonNullElse(matcher.group(1), ""));
    // A v stays part of the rule: a repository publishes either v2.1.0 or 2.1.0,
    // and accepting both would call a switch between the two an update.
    appendLiteral(pattern, prefixLiteral(matcher.group(2)));
    pattern.append("\\d+");
    pattern.append("(?:\\.\\d+)".repeat(Math.max(0, segments - 1)));
    // Segment count is fixed, not "at least": 17.2 and 17.2.1 are two conventions.
    pattern.append(suffixPattern(matcher.group(4)));
    return pattern.append("$").toString();
  }

  /**
   * A tag that is not a fixed version number — latest, stable, 17-alpine, 2.4.0-latest.
   *
   * <p>Such a tag is allowed, but what it points at can change without the
   * recorded value changing with it. Everything that has to reason about the
   * version behind a tag has to know that, which is why the definition lives
   * here rather than once per caller.
   *
   * <p>What decides it is how far the number goes, not what follows it. A tag
   * naming only the major moves with every release of that line — {@code 17},
   * {@code 17-alpine} — while one that also names the minor is what the
   * publisher stopped changing: {@code 17.2}, {@code 17.2-alpine},
   * {@code v4.2.1}, {@code 5:29.7.2}, {@code 6.4.1-15}, {@code alpine-v19.5.2}. Calling those moving
   * puts a component that is pinned exactly as it should be on the task list
   * every day, and a warning that is wrong every day is one nobody reads.
   */
  public static boolean isMovingTag(@Nullable final String tag) {
    if (!nonBlank(tag)) {
      return false;
    }
    if (CHANNEL.matcher(tag.trim()).find()) {
      return true;
    }
    final Matcher matcher = TAG.matcher(tag.trim());
    if (!matcher.matches()) {
      // No number at all, so nothing pins it.
      return true;
    }
    if (matcher.group(3).split("\\.").length < 2) {
      return true;
    }
    final String suffix = matcher.group(4);
    return !suffix.isEmpty() && !VARIANT.matcher(suffix).matches();
  }

  /** True when the tag looks like a preview rather than a release. */
  public static boolean isPrerelease(@Nullable final String tag) {
    return Objects.nonNull(tag) && PRERELEASE.matcher(tag.trim()).matches();
  }

  /**
   * The tag reduced to what may be compared with a version range.
   *
   * <p>The prefix goes — a {@code v} and a Debian epoch are not part of the
   * number. The suffix stays only when it marks a preview: {@code 2.0.0-rc.1}
   * has to keep it, or an rc is read as its own release and answers
   * {@code >=2.0.0} with yes.
   *
   * <p>Every other suffix goes, and that is the part that is easy to get wrong.
   * A semantic version parser reads {@code 6.4.1-15} and {@code 1.2.3-alpine} as
   * prereleases, which sort <em>below</em> the release — so a component running
   * the Debian revision 15 of 6.4.1, or the alpine build of 1.2.3, would fail
   * {@code >=6.4.1} and {@code >=1.2.3}. Both are the release, packaged; the
   * revision and the variant say nothing about which upstream version runs.
   *
   * @return null when the tag carries no version core at all
   */
  @Nullable
  public static String versionForComparison(@Nullable final String tag) {
    if (!nonBlank(tag)) {
      return null;
    }
    final Matcher matcher = TAG.matcher(tag.trim());
    if (!matcher.matches()) {
      return null;
    }
    return isPrerelease(tag) ? matcher.group(3) + matcher.group(4) : matcher.group(3);
  }

  /**
   * The major of a tag, for the constraint that keeps a component on one line.
   *
   * @return null when the tag does not start with a number
   */
  @Nullable
  public static String majorOf(@Nullable final String tag) {
    if (Objects.isNull(tag) || tag.isBlank()) {
      return null;
    }
    final Matcher matcher = TAG.matcher(tag.trim());
    // group(3), not group(2): reading the epoch of 5:29.7.2 as the major would
    // leave a component pinned to 29 matching nothing at all.
    return matcher.matches() ? matcher.group(3).split("\\.")[0] : null;
  }

  /** The v belongs to the tag and stays in the rule; the epoch does not. */
  @Nonnull
  private static String prefixLiteral(@Nullable final String prefix) {
    return Objects.isNull(prefix) || prefix.endsWith(":") ? "" : prefix;
  }

  /**
   * Turns the part behind the version core into pattern text: digits become a
   * number again, everything else has to match literally. A revision such as the
   * {@code -15} of {@code 6.4.1-15} counts up, the {@code -alpine} of
   * {@code 17.2-alpine} does not.
   */
  @Nonnull
  private static String suffixPattern(@Nonnull final String suffix) {
    if (suffix.isEmpty()) {
      return "";
    }
    final StringBuilder pattern = new StringBuilder();
    final Matcher digits = DIGITS.matcher(suffix);
    int position = 0;
    while (digits.find()) {
      appendLiteral(pattern, suffix.substring(position, digits.start()));
      pattern.append("\\d+");
      position = digits.end();
    }
    appendLiteral(pattern, suffix.substring(position));
    return pattern.toString();
  }

  private static void appendLiteral(@Nonnull final StringBuilder pattern, @Nonnull final String literal) {
    if (!literal.isEmpty()) {
      pattern.append(Pattern.quote(literal));
    }
  }
}
