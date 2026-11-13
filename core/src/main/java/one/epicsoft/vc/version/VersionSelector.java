package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.epicsoft.vc.enums.VersionStrategy;
import org.semver4j.Semver;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * Picks the newest of the candidates a source offered.
 *
 * <p>Every refusal here ends up as an error on the check, never as "nothing to
 * do". A rule that matches nothing, a source without the timestamps the chosen
 * strategy needs, a version that will not parse — each of those is a broken
 * setup, and a broken setup that looks like "up to date" stays broken for
 * months.
 */
@Component
public class VersionSelector {

  /**
   * Reduces the candidates to those the rule allows, in the order the strategy
   * defines, newest first.
   *
   * @throws VersionRuleException when the rule or the data cannot answer the question
   */
  @Nonnull
  public List<VersionCandidate> matching(@Nonnull final List<VersionCandidate> candidates,
                                         @Nullable final String tagPattern,
                                         @Nullable final String majorConstraint,
                                         final boolean allowPrerelease,
                                         @Nonnull final VersionStrategy strategy) {
    final Pattern pattern = compile(tagPattern);
    final List<VersionCandidate> allowed = candidates.stream()
        .filter(candidate -> Objects.isNull(pattern) || pattern.matcher(candidate.tag()).matches())
        .filter(candidate -> allowPrerelease || !TagRule.isPrerelease(candidate.tag()))
        .filter(candidate -> !nonBlank(majorConstraint)
                             || Objects.equals(majorConstraint.trim(), TagRule.majorOf(candidate.tag())))
        .toList();
    if (allowed.isEmpty()) {
      throw new VersionRuleException("No candidate matches the rule");
    }

    // Every candidate is measured before anything is sorted. Leaving it to the
    // comparator would skip the check whenever exactly one candidate is left —
    // and a single undated tag would then pass a date strategy that cannot be
    // applied to it, which is the silent wrong answer this class exists to avoid.
    allowed.forEach(candidate -> validate(candidate, strategy));
    return allowed.stream().sorted(comparator(strategy).reversed()).toList();
  }

  /** The one that wins, out of the candidates the rule allows. */
  @Nonnull
  public VersionCandidate latest(@Nonnull final List<VersionCandidate> candidates,
                                 @Nullable final String tagPattern,
                                 @Nullable final String majorConstraint,
                                 final boolean allowPrerelease,
                                 @Nonnull final VersionStrategy strategy) {
    return this.matching(candidates, tagPattern, majorConstraint, allowPrerelease, strategy).getFirst();
  }

  @Nullable
  private static Pattern compile(@Nullable final String tagPattern) {
    if (!nonBlank(tagPattern)) {
      return null;
    }
    try {
      return Pattern.compile(tagPattern);
    } catch (final PatternSyntaxException exception) {
      // A rule nobody can compile must not quietly match everything.
      throw new VersionRuleException("Tag pattern is not a valid expression: " + exception.getDescription());
    }
  }

  /** Reads the value the strategy orders by, and throws when there is none. */
  private static void validate(@Nonnull final VersionCandidate candidate, @Nonnull final VersionStrategy strategy) {
    switch (strategy) {
      case LATEST_BY_DATE, DIGEST -> releasedAt(candidate);
      case SEMVER -> semver(candidate);
      case DEB_VERSION -> debian(candidate);
    }
  }

  @Nonnull
  private static Comparator<VersionCandidate> comparator(@Nonnull final VersionStrategy strategy) {
    return switch (strategy) {
      case LATEST_BY_DATE, DIGEST -> Comparator.comparing(VersionSelector::releasedAt);
      case SEMVER -> Comparator.comparing(VersionSelector::semver);
      case DEB_VERSION -> Comparator.comparing(VersionSelector::debian);
    };
  }

  @Nonnull
  private static Instant releasedAt(@Nonnull final VersionCandidate candidate) {
    if (Objects.isNull(candidate.releasedAt())) {
      // Sorting undated candidates by date would answer at random.
      throw new VersionRuleException("Source provides no release date for tag " + candidate.tag()
                                     + ", so the date strategy cannot be used");
    }
    return candidate.releasedAt();
  }

  @Nonnull
  private static Semver semver(@Nonnull final VersionCandidate candidate) {
    return Optional.ofNullable(VersionComparison.semverOf(candidate.tag()))
                   .orElseThrow(() -> new VersionRuleException("Tag " + candidate.tag() + " is not a version"));
  }

  @Nonnull
  private static DebianVersion debian(@Nonnull final VersionCandidate candidate) {
    return Optional.ofNullable(DebianVersion.parse(candidate.tag()))
                   .orElseThrow(() -> new VersionRuleException(
                       "Version " + candidate.tag() + " is not a Debian version"));
  }
}
