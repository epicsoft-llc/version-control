package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.VersionStrategy;
import org.semver4j.Semver;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The next permissible target, and separately the jump that is out of reach.
 *
 * <p>The application does not show "the newest version". For infrastructure the
 * jump to the newest is often not one action but a project, and a screen that
 * demands it every day is a screen that gets ignored. So the answer comes in two
 * parts: what can be done now, and a note that something further exists.
 *
 * <p>Only the first part becomes a task. The second one carries no urgency on
 * purpose — a permanent red state that nobody can resolve teaches people to live
 * with red states.
 */
@Component
public class UpgradePathCalculator {

  /** The version core at the start of a tag, whatever the publisher appends. */
  private static final Pattern LEADING_NUMBERS = Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)");

  /**
   * Works out what to propose.
   *
   * @param current    what is deployed
   * @param candidates every version the source offers, unfiltered
   * @param maxStep    how far one step may go
   * @param strategy   how two versions are ordered
   */
  @Nonnull
  public UpgradeTarget calculate(@Nullable final String current,
                                 @Nonnull final List<String> candidates,
                                 @Nullable final MaxStep maxStep,
                                 @Nullable final VersionStrategy strategy) {
    if (Objects.isNull(current) || current.isBlank() || candidates.isEmpty()) {
      return UpgradeTarget.none();
    }
    final List<String> newer = candidates.stream()
                                         .filter(candidate -> VersionComparison.isBehind(current, candidate, strategy))
                                         .sorted(comparator(strategy))
                                         .toList();
    if (newer.isEmpty()) {
      return UpgradeTarget.none();
    }
    final String newest = newer.getLast();

    if (MaxStep.NONE.equals(maxStep)) {
      // Nothing is proposed, but what exists is still shown: switching updates
      // off is a decision about acting, not about knowing.
      return new UpgradeTarget(null, newest, true);
    }

    final String target = newer.stream()
                               .filter(candidate -> withinStep(current, candidate, maxStep))
                               .reduce((first, second) -> second)
                               .orElse(null);
    if (Objects.isNull(target)) {
      // Everything available is past the limit: a note, and no task.
      return new UpgradeTarget(null, newest, true);
    }
    // The note only where something is actually left over beyond the target.
    return new UpgradeTarget(target, target.equals(newest) ? null : newest, !target.equals(newest));
  }

  /**
   * Whether one step from here to there stays inside the limit.
   *
   * <p>Measured on the segments as the component publishes them, not through a
   * semantic version: PostgreSQL numbers 16.4 and 16.6, and reading the second
   * segment as a minor would mean a patch-only component is never offered its own
   * patches. So a patch keeps everything but the last segment, a minor keeps the
   * first, and that holds for two and three segment schemes alike.
   *
   * <p>A version whose segments cannot be read is treated as inside the limit.
   * Hiding an update because its numbering is unusual is worse than proposing one
   * that turns out to be too large — the second is visible, the first is not.
   */
  private static boolean withinStep(@Nonnull final String current, @Nonnull final String candidate,
                                    @Nullable final MaxStep maxStep) {
    if (Objects.isNull(maxStep) || MaxStep.MAJOR.equals(maxStep)) {
      return true;
    }
    final List<Long> from = segments(current);
    final List<Long> to = segments(candidate);
    if (from.isEmpty() || to.isEmpty()) {
      return true;
    }
    if (!Objects.equals(from.getFirst(), to.getFirst())) {
      return false;
    }
    if (!MaxStep.PATCH.equals(maxStep)) {
      return true;
    }
    // Everything but the last segment has to match, and a scheme with a single
    // segment has no patch level at all.
    final int shared = Math.min(from.size(), to.size()) - 1;
    if (shared < 1 || from.size() != to.size()) {
      return false;
    }
    return from.subList(0, shared).equals(to.subList(0, shared));
  }

  /** The leading numeric segments; for a Debian version those of its upstream part. */
  @Nonnull
  private static List<Long> segments(@Nonnull final String version) {
    final DebianVersion debian = DebianVersion.parse(version);
    final String core = Objects.isNull(debian) ? version : debian.upstream();
    final Matcher matcher = LEADING_NUMBERS.matcher(core.trim());
    if (!matcher.find()) {
      return List.of();
    }
    return Arrays.stream(matcher.group(1).split("\\.")).map(Long::valueOf).toList();
  }

  /**
   * Orders by the parsed version, not by asking "is a behind b" pairwise: for two
   * versions that cannot be parsed that question answers yes in both directions,
   * and a sort built on it throws rather than ordering anything. Unparseable
   * versions sort first and are then separated by their text, which at least
   * makes the result the same every time.
   */
  @Nonnull
  private static Comparator<String> comparator(@Nullable final VersionStrategy strategy) {
    if (VersionStrategy.DEB_VERSION.equals(strategy)) {
      return Comparator.comparing((String version) -> DebianVersion.parse(version),
                                  Comparator.nullsFirst(Comparator.<DebianVersion>naturalOrder()))
                       .thenComparing(Comparator.naturalOrder());
    }
    return Comparator.comparing((String version) -> VersionComparison.semverOf(version),
                                Comparator.nullsFirst(Comparator.<Semver>naturalOrder()))
                     .thenComparing(Comparator.naturalOrder());
  }
}
