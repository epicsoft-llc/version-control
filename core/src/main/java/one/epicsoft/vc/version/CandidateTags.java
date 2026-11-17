package one.epicsoft.vc.version;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.jpa.entity.ComponentPackageEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * How the candidates of a check are stored, and read back.
 *
 * <p>One place for the format. It was written in one class and read in two
 * others, and a format that is written once and parsed twice drifts apart the
 * first time somebody adds a field.
 *
 * <p>The date and the digest ride along, not only the tag: a reader that dropped
 * them could not order by date, and the strategy would then work on fresh data
 * and fail on stored data.
 */
public final class CandidateTags {

  private static final String CANDIDATE_SEPARATOR = "\n";
  private static final String FIELD_SEPARATOR = "\t";

  /** Enough for a rule preview; the full list of an old repository is noise. */
  private static final int STORED_CANDIDATES = 200;

  /** Debian order, newest first; a version it cannot read goes last instead of being guessed into place. */
  private static final Comparator<VersionCandidate> NEWEST_FIRST =
      Comparator.comparing((VersionCandidate candidate) -> DebianVersion.parse(candidate.tag()),
                           Comparator.nullsLast(Comparator.<DebianVersion>reverseOrder()));

  private CandidateTags() {
    // utility class
  }

  @Nonnull
  public static String serialize(@Nonnull final List<VersionCandidate> candidates) {
    return candidates.stream()
                     .limit(STORED_CANDIDATES)
                     .map(CandidateTags::line)
                     .collect(Collectors.joining(CANDIDATE_SEPARATOR));
  }

  /**
   * A package index lists every package of the repository in its own order, so one cap over all of them can cut off
   * the leading package entirely. A component made of packages keeps the newest versions of each configured package,
   * the leading one first.
   */
  @Nonnull
  public static String serialize(@Nonnull final List<VersionCandidate> candidates,
                                 @Nonnull final List<ComponentPackageEntity> packages) {
    return serialize(packages.isEmpty() ? candidates : newestPerPackage(candidates, packages));
  }

  @Nonnull
  public static List<VersionCandidate> parse(final String stored) {
    if (!nonBlank(stored)) {
      return List.of();
    }
    final List<VersionCandidate> candidates = new ArrayList<>();
    for (final String line : stored.split(CANDIDATE_SEPARATOR)) {
      if (line.isBlank()) {
        continue;
      }
      final String[] fields = line.split(FIELD_SEPARATOR, -1);
      candidates.add(new VersionCandidate(fields[0],
                                          fields.length > 1 && nonBlank(fields[1]) ? Instant.parse(fields[1]) : null,
                                          fields.length > 2 && nonBlank(fields[2]) ? fields[2] : null,
                                          fields.length > 3 && nonBlank(fields[3]) ? fields[3] : null));
    }
    return candidates;
  }

  @Nonnull
  private static List<VersionCandidate> newestPerPackage(@Nonnull final List<VersionCandidate> candidates,
                                                         @Nonnull final List<ComponentPackageEntity> packages) {
    final int perPackage = Math.max(1, STORED_CANDIDATES / packages.size());
    final Map<String, List<VersionCandidate>> byPackage =
        candidates.stream()
                  .filter(candidate -> nonBlank(candidate.packageName()))
                  .collect(Collectors.groupingBy(VersionCandidate::packageName));
    return packages.stream()
                   .sorted(Comparator.comparing(ComponentPackageEntity::isLeadingPackage).reversed())
                   .flatMap(entry -> byPackage.getOrDefault(entry.getName(), List.of()).stream()
                                              .sorted(NEWEST_FIRST)
                                              .limit(perPackage))
                   .toList();
  }

  @Nonnull
  private static String line(@Nonnull final VersionCandidate candidate) {
    return String.join(FIELD_SEPARATOR,
                       candidate.tag(),
                       isNull(candidate.releasedAt()) ? "" : candidate.releasedAt().toString(),
                       isNull(candidate.digest()) ? "" : candidate.digest(),
                       isNull(candidate.packageName()) ? "" : candidate.packageName());
  }
}
