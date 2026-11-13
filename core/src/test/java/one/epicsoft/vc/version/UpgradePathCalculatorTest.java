package one.epicsoft.vc.version;

import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.VersionStrategy;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What can be done now, kept apart from what merely exists. The example from the
 * specification is the first test: on 16.4 with 18.2 available and minor steps
 * allowed, the answer is 16.9 plus a note, not 18.2 as a demand.
 */
class UpgradePathCalculatorTest {

  private final UpgradePathCalculator calculator = new UpgradePathCalculator();

  private static final List<String> POSTGRES = List.of("16.4", "16.6", "16.9", "17.2", "18.0", "18.2");

  @Test
  void proposesTheHighestVersionInsideTheLimitAndNotesTheRest() {
    final UpgradeTarget target = this.calculator.calculate("16.4", POSTGRES, MaxStep.MINOR, VersionStrategy.SEMVER);

    assertEquals("16.9", target.target());
    // A note, not a demand: the major jump is a project, not an action.
    assertEquals("18.2", target.beyond());
    assertTrue(target.limitedBy());
  }

  @Test
  void readsTheLastSegmentAsThePatchWhateverTheSchemeIs() {
    // PostgreSQL numbers 16.4 and 16.6, and that step is a patch in its own
    // scheme. Reading the second segment as a minor would mean a patch-only
    // component is never offered its own patches.
    assertEquals("16.9", this.calculator.calculate("16.4", List.of("16.4", "16.6", "16.9"), MaxStep.PATCH,
                                                   VersionStrategy.SEMVER).target());
    // Three segments, same rule: everything but the last has to match.
    assertEquals("1.2.9", this.calculator.calculate("1.2.3", List.of("1.2.9", "1.3.0"), MaxStep.PATCH,
                                                    VersionStrategy.SEMVER).target());
    assertEquals("1.3.0", this.calculator.calculate("1.2.3", List.of("1.2.9", "1.3.0"), MaxStep.MINOR,
                                                    VersionStrategy.SEMVER).target());
  }

  @Test
  void proposesTheNewestWhenNothingLimitsIt() {
    final UpgradeTarget target = this.calculator.calculate("16.4", POSTGRES, MaxStep.MAJOR, VersionStrategy.SEMVER);

    assertEquals("18.2", target.target());
    // Nothing is left over, so there is nothing to note.
    assertNull(target.beyond());
    assertFalse(target.limitedBy());
  }

  @Test
  void proposesNothingButStillShowsWhatExists() {
    final UpgradeTarget target = this.calculator.calculate("16.4", POSTGRES, MaxStep.NONE, VersionStrategy.SEMVER);

    // Switching updates off is a decision about acting, not about knowing.
    assertNull(target.target());
    assertEquals("18.2", target.beyond());
  }

  @Test
  void proposesNothingWhenEverythingAvailableIsPastTheLimit() {
    final UpgradeTarget target = this.calculator.calculate("16.4", List.of("17.0", "18.2"), MaxStep.MINOR,
                                                           VersionStrategy.SEMVER);

    // No task, because there is nothing that can be done in one step - but the
    // reader still sees that something is out there.
    assertNull(target.target());
    assertEquals("18.2", target.beyond());
  }

  @Test
  void hasNothingToSayWhenTheCurrentVersionIsTheNewest() {
    final UpgradeTarget target = this.calculator.calculate("18.2", POSTGRES, MaxStep.MAJOR, VersionStrategy.SEMVER);

    assertFalse(target.hasTarget());
    assertNull(target.beyond());
  }

  @Test
  void ordersDebianVersionsByTheirOwnRules() {
    final List<String> versions = List.of("5:29.7.2-1~ubuntu.24.04~noble",
                                          "5:29.7.10-1~ubuntu.24.04~noble",
                                          "5:29.7.9-1~ubuntu.24.04~noble");

    final UpgradeTarget target = this.calculator.calculate("5:29.7.2-1~ubuntu.24.04~noble", versions, MaxStep.MAJOR,
                                                           VersionStrategy.DEB_VERSION);

    // 29.7.10 is newer than 29.7.9, which a string comparison gets backwards.
    assertEquals("5:29.7.10-1~ubuntu.24.04~noble", target.target());
  }

  @Test
  void readsASuffixOfDigitsAloneAsTheBuildOfTheRelease() {
    // Zammad publishes every release many times over, and a new build is an update of its own.
    assertEquals("7.2.2-0047", this.calculator.calculate("7.2.2-0045", List.of("7.2.2-0045", "7.2.2-0047"), MaxStep.PATCH,
                                                         VersionStrategy.SEMVER).target());
    // The release decides before the build does, and two segments are read the same way.
    assertEquals("7.2.3-0000", this.calculator.calculate("7.2.2-0047", List.of("7.2.2-0045", "7.2.3-0000"), MaxStep.MINOR,
                                                         VersionStrategy.SEMVER).target());
    assertEquals("17.2-46", this.calculator.calculate("17.2-45", List.of("17.2-46"), MaxStep.PATCH,
                                                      VersionStrategy.SEMVER).target());
  }

  @Test
  void treatsAVersionItCannotReadAsInsideTheLimit() {
    // Hiding an update because its numbering is unusual is worse than proposing
    // one that turns out to be too large: the second is visible, the first is not.
    assertEquals("nightly-2", this.calculator.calculate("nightly-1", List.of("nightly-2"), MaxStep.PATCH,
                                                        VersionStrategy.SEMVER).target());
  }
}
