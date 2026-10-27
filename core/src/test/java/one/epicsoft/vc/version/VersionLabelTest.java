package one.epicsoft.vc.version;

import one.epicsoft.vc.enums.DebianVersionDisplay;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The short form is the default, the full one a setting away, and nothing that is not a package version changes. */
class VersionLabelTest {

  private static final String DOCKER = "5:29.9.2-1~ubuntu.24.04~noble";

  @Test
  void shortensAPackageVersion() {
    assertEquals("29.9.2-1", VersionLabel.of(DOCKER, DebianVersionDisplay.SHORT));
  }

  @Test
  void writesThePackageVersionInFullWhenAskedTo() {
    assertEquals(DOCKER, VersionLabel.of(DOCKER, DebianVersionDisplay.FULL));
  }

  @Test
  void leavesEverythingElseAsItIs() {
    // Image and release tags, a variant, a digest: nothing to shorten, and nothing may change.
    for (final String version : List.of("v3.241.0", "18-alpine", "17.6", "1.2.3-rc.1", "latest",
                                        "sha256:0a1b2c3d")) {
      assertEquals(version, VersionLabel.of(version, DebianVersionDisplay.SHORT));
    }
  }
}
