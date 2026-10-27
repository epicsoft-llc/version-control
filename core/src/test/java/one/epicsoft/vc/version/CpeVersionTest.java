package one.epicsoft.vc.version;

import one.epicsoft.vc.data.CpeRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a recorded version is read against the CPE dictionary, and which entries meet it. */
class CpeVersionTest {

  @Test
  void readsTheProductVersionOutOfWhatWasRecorded() {
    assertEquals("17.6", CpeVersion.normalize("17.6-alpine"));
    assertEquals("3.241.0", CpeVersion.normalize("v3.241.0"));
    // A Debian package version: the upstream part, without epoch, revision and distribution.
    assertEquals("29.9.2", CpeVersion.normalize("5:29.9.2-1~ubuntu.24.04~noble"));
    assertEquals("18", CpeVersion.normalize("18-alpine"));
  }

  @Test
  void findsNothingToCompareInATagWithoutAVersion() {
    assertNull(CpeVersion.normalize("latest"));
    assertNull(CpeVersion.normalize("sha256:0a1b2c3d"));
  }

  @Test
  void comparesNumbersAsNumbersAndAMissingOneAsZero() {
    assertTrue(CpeVersion.compare("17.10", "17.9") > 0);
    assertEquals(0, CpeVersion.compare("17.6", "17.6.0"));
    // A candidate comes before its release.
    assertTrue(CpeVersion.compare("3.0.0-rc1", "3.0.0") < 0);
  }

  @Test
  void meetsAnEntryForExactlyThatVersion() {
    assertTrue(CpeVersion.affects(exact("17.2"), "17.2"));
    assertFalse(CpeVersion.affects(exact("17.2"), "17.3"));
  }

  @Test
  void respectsEveryKindOfBound() {
    final CpeRange range = new CpeRange("CVE-2026-1", "*", "17.0", null, null, "17.6", null, null, false, null);

    assertTrue(CpeVersion.affects(range, "17.0"));
    assertTrue(CpeVersion.affects(range, "17.5.9"));
    assertFalse(CpeVersion.affects(range, "17.6"));
    assertFalse(CpeVersion.affects(range, "16.9"));

    final CpeRange open = new CpeRange("CVE-2026-2", "*", null, "2.0", "2.4", null, null, null, false, null);
    assertFalse(CpeVersion.affects(open, "2.0"));
    assertTrue(CpeVersion.affects(open, "2.4"));
  }

  @Test
  void ignoresAnEntryThatNamesNoVersionAtAll() {
    // It would claim every version ever released, and mark each one of them.
    assertFalse(CpeVersion.affects(new CpeRange("CVE-2026-3", "*", null, null, null, null, null, null, false, null), "17.2"));
  }

  private static CpeRange exact(final String version) {
    return new CpeRange("CVE-2026-0", version, null, null, null, null, null, null, false, null);
  }
}
