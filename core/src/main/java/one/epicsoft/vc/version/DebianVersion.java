package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Debian package version, and the comparison that goes with it.
 *
 * <p>{@code 5:29.7.2-1~ubuntu.24.04~noble} is an epoch, an upstream version, a
 * revision and a distribution suffix, and it is not a semantic version. Handing
 * it to a SemVer library gives a wrong answer rather than an error, which is why
 * this is written out and tested here.
 *
 * <p>Three rules carry the whole thing:
 *
 * <ul>
 *   <li>The epoch beats everything else — {@code 5:1.0} is newer than
 *       {@code 4:99.0}.</li>
 *   <li>Digits compare as numbers, so {@code 29.7.10} is newer than
 *       {@code 29.7.2}. A plain string comparison gets this backwards.</li>
 *   <li>A tilde sorts <em>before</em> the empty rest, so {@code 1.0~rc1} is
 *       older than {@code 1.0}. Every naive comparison fails on this one.</li>
 * </ul>
 *
 * <p>The distribution suffix rides along inside the revision, where the ordering
 * rules put it. What is shown to a reader, and what decides whether a change is
 * a patch or a major step, is {@link #upstream()} alone.
 */
public final class DebianVersion implements Comparable<DebianVersion> {

  private static final Pattern EPOCH = Pattern.compile("^(\\d+):(.*)$");

  private final int epoch;
  @Nonnull
  private final String upstream;
  @Nonnull
  private final String revision;
  @Nonnull
  private final String full;

  private DebianVersion(final int epoch, @Nonnull final String upstream, @Nonnull final String revision,
                        @Nonnull final String full) {
    this.epoch = epoch;
    this.upstream = upstream;
    this.revision = revision;
    this.full = full;
  }

  /** Null for anything that is not a Debian version — the caller reports that, never guesses. */
  @Nullable
  public static DebianVersion parse(@Nullable final String value) {
    if (Objects.isNull(value) || value.isBlank()) {
      return null;
    }
    final String trimmed = value.trim();
    final Matcher matcher = EPOCH.matcher(trimmed);
    final boolean hasEpoch = matcher.matches();
    final int epoch = hasEpoch ? Integer.parseInt(matcher.group(1)) : 0;
    final String rest = hasEpoch ? matcher.group(2) : trimmed;
    if (rest.isBlank() || rest.indexOf(':') >= 0) {
      return null;
    }

    // The last hyphen splits, not the first: an upstream version may carry one of
    // its own, and 2.1-beta-1 is upstream 2.1-beta with revision 1.
    final int split = rest.lastIndexOf('-');
    final String upstream = split < 0 ? rest : rest.substring(0, split);
    final String revision = split < 0 ? "" : rest.substring(split + 1);
    if (upstream.isBlank() || !Character.isDigit(upstream.charAt(0))) {
      // Debian policy: an upstream version starts with a digit. Anything else is
      // not a version this can order, and saying so beats ordering it wrongly.
      return null;
    }
    return new DebianVersion(epoch, upstream, revision, trimmed);
  }

  /**
   * The part a reader recognises, and the part a patch or major step is measured
   * on: {@code 29.7.2} out of {@code 5:29.7.2-1~ubuntu.24.04~noble}.
   */
  @Nonnull
  public String upstream() {
    return this.upstream;
  }

  /** The version as it was written, which is what an installed package reports. */
  @Nonnull
  public String full() {
    return this.full;
  }

  @Override
  public int compareTo(@Nonnull final DebianVersion other) {
    if (this.epoch != other.epoch) {
      return Integer.compare(this.epoch, other.epoch);
    }
    final int upstreamOrder = comparePart(this.upstream, other.upstream);
    return upstreamOrder != 0 ? upstreamOrder : comparePart(this.revision, other.revision);
  }

  /**
   * Same epoch, upstream version and package revision. A tilde in the revision starts the distribution a package
   * was built for, so {@code -1~ubuntu.24.04~noble} and {@code -1~ubuntu.26.04~resolute} are one release.
   */
  public boolean isSameRelease(@Nonnull final DebianVersion other) {
    return this.epoch == other.epoch
           && comparePart(this.upstream, other.upstream) == 0
           && comparePart(packageRevision(this.revision), packageRevision(other.revision)) == 0;
  }

  @Nonnull
  private static String packageRevision(@Nonnull final String revision) {
    final int tilde = revision.indexOf('~');
    return tilde < 0 ? revision : revision.substring(0, tilde);
  }

  // No equals and no hashCode on purpose. Ordering here is not equality: 1.007
  // and 1.7 are the same version and would have to share a hash, while the
  // strings they came from do not. Comparison goes through compareTo, which is
  // the only question this class answers.

  @Override
  @Nonnull
  public String toString() {
    return this.full;
  }

  /**
   * The comparison Debian policy describes: alternating runs of non-digits and
   * digits, digits as numbers, and inside a non-digit run the letters first,
   * everything else after them — with the tilde ahead of them all, ahead even of
   * the end of the string.
   */
  private static int comparePart(@Nonnull final String first, @Nonnull final String second) {
    int firstIndex = 0;
    int secondIndex = 0;
    while (firstIndex < first.length() || secondIndex < second.length()) {
      int order = 0;
      while ((firstIndex < first.length() && !isDigit(first.charAt(firstIndex)))
             || (secondIndex < second.length() && !isDigit(second.charAt(secondIndex)))) {
        final int firstOrder = orderOf(firstIndex < first.length() ? first.charAt(firstIndex) : '\0');
        final int secondOrder = orderOf(secondIndex < second.length() ? second.charAt(secondIndex) : '\0');
        if (firstOrder != secondOrder) {
          return Integer.compare(firstOrder, secondOrder);
        }
        firstIndex++;
        secondIndex++;
      }
      // Leading zeros carry no meaning: 1.007 and 1.7 are the same version.
      while (firstIndex < first.length() && first.charAt(firstIndex) == '0') {
        firstIndex++;
      }
      while (secondIndex < second.length() && second.charAt(secondIndex) == '0') {
        secondIndex++;
      }
      while (firstIndex < first.length() && isDigit(first.charAt(firstIndex))
             && secondIndex < second.length() && isDigit(second.charAt(secondIndex))) {
        if (order == 0) {
          order = Integer.compare(first.charAt(firstIndex), second.charAt(secondIndex));
        }
        firstIndex++;
        secondIndex++;
      }
      // The longer run of digits is the larger number, whatever its first digit.
      if (firstIndex < first.length() && isDigit(first.charAt(firstIndex))) {
        return 1;
      }
      if (secondIndex < second.length() && isDigit(second.charAt(secondIndex))) {
        return -1;
      }
      if (order != 0) {
        return order;
      }
    }
    return 0;
  }

  private static boolean isDigit(final char character) {
    return character >= '0' && character <= '9';
  }

  /** The tilde first, then the end of the string, then letters, then the rest. */
  private static int orderOf(final char character) {
    if (character == '~') {
      return -1;
    }
    if (character == '\0') {
      return 0;
    }
    return (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z')
           ? character
           : character + 256;
  }
}
