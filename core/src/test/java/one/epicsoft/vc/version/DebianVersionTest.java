package one.epicsoft.vc.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three rules that a naive comparison gets wrong, and the parsing around
 * them. Every case here is one that has actually bitten somebody.
 */
class DebianVersionTest {

  @Test
  void comparesDigitsAsNumbers() {
    // The one that matters most in practice: 10 comes after 2, and a string
    // comparison says the opposite.
    assertTrue(newer("5:29.7.10-1~ubuntu.24.04~noble", "5:29.7.2-1~ubuntu.24.04~noble"));
  }

  @Test
  void letsTheEpochBeatEverythingElse() {
    assertTrue(newer("5:1.0", "4:99.0"));
  }

  @Test
  void sortsATildeBeforeTheEmptyRest() {
    // 1.0~rc1 is a release candidate for 1.0 and therefore older than it.
    assertTrue(newer("1.0", "1.0~rc1"));
  }

  @Test
  void comparesTheRevisionWhenTheUpstreamVersionIsEqual() {
    assertTrue(newer("29.7.2-2", "29.7.2-1"));
  }

  @Test
  void ignoresLeadingZeros() {
    final DebianVersion padded = DebianVersion.parse("1.007");
    final DebianVersion plain = DebianVersion.parse("1.7");
    assertNotNull(padded);
    assertNotNull(plain);
    // Ordering, not equality: the class deliberately has no equals, because two
    // versions that order the same came from strings that do not.
    assertEquals(0, padded.compareTo(plain));
  }

  @Test
  void keepsTheFullVersionAndOffersTheUpstreamPart() {
    final DebianVersion version = DebianVersion.parse("5:29.7.2-1~ubuntu.24.04~noble");

    assertNotNull(version);
    // The full string is what an installed package reports and what gets stored.
    assertEquals("5:29.7.2-1~ubuntu.24.04~noble", version.full());
    // The upstream part is what a reader recognises and what a patch step is measured on.
    assertEquals("29.7.2", version.upstream());
  }

  @Test
  void readsABuildForAnotherDistributionAsTheSameRelease() {
    assertTrue(sameRelease("5:29.9.2-1~ubuntu.24.04~noble", "5:29.9.2-1~ubuntu.26.04~resolute"));
  }

  @Test
  void tellsADifferentRevisionOrUpstreamVersionApart() {
    assertFalse(sameRelease("5:29.9.2-1~ubuntu.24.04~noble", "5:29.9.2-2~ubuntu.26.04~resolute"));
    assertFalse(sameRelease("5:29.9.2-1~ubuntu.24.04~noble", "5:29.9.3-1~ubuntu.24.04~noble"));
    // A tilde in the upstream version is a pre-release, not a distribution.
    assertFalse(sameRelease("1.0~rc1-1", "1.0-1"));
  }

  @Test
  void splitsAtTheLastHyphen() {
    final DebianVersion version = DebianVersion.parse("2.1-beta-3");

    assertNotNull(version);
    assertEquals("2.1-beta", version.upstream());
  }

  @Test
  void refusesWhatIsNotADebianVersion() {
    // Saying so beats ordering it wrongly: everything here ends as a visible error.
    assertNull(DebianVersion.parse("latest"));
    assertNull(DebianVersion.parse(""));
    assertNull(DebianVersion.parse(null));
  }

  private static boolean newer(final String first, final String second) {
    final DebianVersion left = DebianVersion.parse(first);
    final DebianVersion right = DebianVersion.parse(second);
    assertNotNull(left);
    assertNotNull(right);
    return left.compareTo(right) > 0;
  }

  private static boolean sameRelease(final String first, final String second) {
    final DebianVersion left = DebianVersion.parse(first);
    final DebianVersion right = DebianVersion.parse(second);
    assertNotNull(left);
    assertNotNull(right);
    return left.isSameRelease(right);
  }
}
