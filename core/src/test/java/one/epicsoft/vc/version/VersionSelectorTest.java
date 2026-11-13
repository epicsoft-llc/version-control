package one.epicsoft.vc.version;

import one.epicsoft.vc.enums.VersionStrategy;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Picking the winner, and above all the four ways of refusing to pick one. Every
 * refusal here becomes a visible error on the check; the alternative would be a
 * component that silently looks up to date for months.
 */
class VersionSelectorTest {

  private final VersionSelector selector = new VersionSelector();

  @Test
  void takesTheNewestByDate() {
    final List<VersionCandidate> candidates = List.of(
      VersionCandidate.of("17.2", Instant.parse("2026-01-05T00:00:00Z"), "sha256:a"),
      VersionCandidate.of("17.4", Instant.parse("2026-03-05T00:00:00Z"), "sha256:b"),
      VersionCandidate.of("17.3", Instant.parse("2026-02-05T00:00:00Z"), "sha256:c"));

    final VersionCandidate latest = this.selector.latest(candidates, "^\\d+\\.\\d+$", null, false,
                                                         VersionStrategy.LATEST_BY_DATE);

    assertEquals("17.4", latest.tag());
  }

  @Test
  void takesTheHighestBySemver() {
    final List<VersionCandidate> candidates =
      List.of(VersionCandidate.of("1.9.0"), VersionCandidate.of("1.10.0"), VersionCandidate.of("1.2.0"));

    // The case a string comparison gets wrong: 1.10.0 is newer than 1.9.0.
    assertEquals("1.10.0", this.selector.latest(candidates, null, null, false, VersionStrategy.SEMVER).tag());
  }

  @Test
  void takesTheHighestBuildOfARelease() {
    // In the order of the registry, the last push first: the build number decides, not the order.
    final List<VersionCandidate> candidates =
      List.of(VersionCandidate.of("7.2.2-0045"), VersionCandidate.of("7.2.2-0047"), VersionCandidate.of("7.2.2-0046"));

    assertEquals("7.2.2-0047", this.selector.latest(candidates, null, null, false, VersionStrategy.SEMVER).tag());
  }

  @Test
  void takesTheHighestDebianVersion() {
    final List<VersionCandidate> candidates = List.of(
      VersionCandidate.ofPackage("docker-ce", "5:29.7.2-1~ubuntu.24.04~noble"),
      VersionCandidate.ofPackage("docker-ce", "5:29.7.10-1~ubuntu.24.04~noble"),
      VersionCandidate.ofPackage("docker-ce", "5:29.7.9-1~ubuntu.24.04~noble"));

    assertEquals("5:29.7.10-1~ubuntu.24.04~noble",
                 this.selector.latest(candidates, null, null, false, VersionStrategy.DEB_VERSION).tag());
  }

  @Test
  void leavesPrereleasesOutUnlessTheyAreWanted() {
    final List<VersionCandidate> candidates =
      List.of(VersionCandidate.of("2.0.0"), VersionCandidate.of("2.1.0-rc.1"));

    assertEquals("2.0.0", this.selector.latest(candidates, null, null, false, VersionStrategy.SEMVER).tag());
    assertEquals("2.1.0-rc.1", this.selector.latest(candidates, null, null, true, VersionStrategy.SEMVER).tag());
  }

  @Test
  void staysOnTheMajorItIsConstrainedTo() {
    final List<VersionCandidate> candidates =
      List.of(VersionCandidate.of("17.2"), VersionCandidate.of("18.0"), VersionCandidate.of("17.4"));

    assertEquals("17.4", this.selector.latest(candidates, null, "17", false, VersionStrategy.SEMVER).tag());
  }

  @Test
  void refusesWhenTheRuleMatchesNothing() {
    final List<VersionCandidate> candidates = List.of(VersionCandidate.of("17.2-bookworm"));

    // A rule that fits no tag is a broken rule, not a component that is up to date.
    assertThrows(VersionRuleException.class,
                 () -> this.selector.latest(candidates, "^\\d+\\.\\d+-alpine$", null, false, VersionStrategy.SEMVER));
  }

  @Test
  void refusesAPatternThatDoesNotCompile() {
    assertThrows(VersionRuleException.class,
                 () -> this.selector.latest(List.of(VersionCandidate.of("1.0")), "^[", null, false,
                                            VersionStrategy.SEMVER));
  }

  @Test
  void refusesToSortUndatedCandidatesByDate() {
    // A registry that lists names and nothing else cannot answer this question,
    // and inventing an order would answer it at random.
    assertThrows(VersionRuleException.class,
                 () -> this.selector.latest(List.of(VersionCandidate.of("1.0")), null, null, false,
                                            VersionStrategy.LATEST_BY_DATE));
  }

  @Test
  void refusesAVersionItCannotRead() {
    assertThrows(VersionRuleException.class,
                 () -> this.selector.latest(List.of(VersionCandidate.of("nightly")), null, null, false,
                                            VersionStrategy.DEB_VERSION));
  }
}
