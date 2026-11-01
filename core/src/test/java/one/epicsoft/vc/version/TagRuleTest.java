package one.epicsoft.vc.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rule a component gets when nobody wrote one, derived from what it runs. */
class TagRuleTest {

  @Test
  void keepsTheVariantOfTheDeployedTag() {
    final String pattern = TagRule.derivePattern("17.2-alpine");

    assertNotNull(pattern);
    assertTrue("17.3-alpine".matches(pattern));
    // Changing the variant is not an update, it is a different image.
    assertFalse("17.3-bookworm".matches(pattern));
    assertFalse("17.3".matches(pattern));
  }

  @Test
  void keepsTheNumberOfSegments() {
    final String pattern = TagRule.derivePattern("17.2");

    assertNotNull(pattern);
    assertTrue("18.0".matches(pattern));
    // A component that publishes 17, 17.2 and 17.2.1 would otherwise jump between
    // three conventions and call each of them an update.
    assertFalse("17".matches(pattern));
    assertFalse("17.2.1".matches(pattern));
  }

  @Test
  void treatsANumericSuffixAsANumber() {
    final String pattern = TagRule.derivePattern("6.4.1-15");

    assertNotNull(pattern);
    assertTrue("6.4.1-16".matches(pattern));
    assertTrue("6.5.0-2".matches(pattern));
    assertFalse("6.4.1-alpine".matches(pattern));
  }

  @Test
  void handlesTheLeadingVOfAReleaseTag() {
    final String pattern = TagRule.derivePattern("v2.1.0");

    assertNotNull(pattern);
    // The dominant form on GitHub. Without it every release component without an
    // explicit pattern would sit in ERROR for good.
    assertTrue("v2.2.0".matches(pattern));
    // The v is part of the rule: a repository publishes one form or the other,
    // and a switch between them is not an update.
    assertFalse("2.2.0".matches(pattern));
    assertEquals("2", TagRule.majorOf("v2.1.0"));
  }

  @Test
  void readsTheMajorOfADebianVersionRatherThanItsEpoch() {
    // 5 is the epoch. A component pinned to major 29 would otherwise match
    // nothing at all and report a rule error for a correct rule.
    assertEquals("29", TagRule.majorOf("5:29.7.2-1~ubuntu.24.04~noble"));
    assertEquals("2", TagRule.majorOf("2.3.3-1"));
  }

  @Test
  void derivesNothingFromATagThatMoves() {
    // latest and stable carry no version core. The honest answer is no rule, and
    // the caller turns that into a visible error rather than a guess.
    assertNull(TagRule.derivePattern("latest"));
    assertNull(TagRule.derivePattern("stable"));
    assertNull(TagRule.derivePattern(null));
  }

  @Test
  void recognisesAPrerelease() {
    assertTrue(TagRule.isPrerelease("2.0.0-rc.1"));
    assertTrue(TagRule.isPrerelease("2.0.0-beta2"));
    assertFalse(TagRule.isPrerelease("2.0.0"));
    // A variant is not a prerelease, however much it looks like a suffix.
    assertFalse(TagRule.isPrerelease("17.2-alpine"));
  }

  @Test
  void readsTheMajorOfATag() {
    assertEquals("17", TagRule.majorOf("17.2-alpine"));
    assertEquals("6", TagRule.majorOf("6.4.1-15"));
    assertNull(TagRule.majorOf("latest"));
  }

  @Test
  void keepsAVariantInFrontOfTheVersion() {
    final String pattern = TagRule.derivePattern("alpine-v19.5.2");

    assertNotNull(pattern);
    assertTrue("alpine-v19.6.0".matches(pattern));
    assertFalse("ubuntu-v19.6.0".matches(pattern));
    assertFalse("v19.6.0".matches(pattern));
    assertEquals("19", TagRule.majorOf("alpine-v19.5.2"));
    assertEquals("1.6.16", TagRule.versionForComparison("rest-api-1.6.16"));
  }

  @Test
  void tellsAPinnedTagFromOneThatMoves() {
    assertFalse(TagRule.isMovingTag("alpine-v19.5.2"));
    assertFalse(TagRule.isMovingTag("rest-api-1.6.16"));
    assertFalse(TagRule.isMovingTag("standalone-chrome-4.1.4"));
    assertFalse(TagRule.isMovingTag("1.30.6-alpine3.24"));
    assertFalse(TagRule.isMovingTag("26.10.0.133078-community"));
    assertTrue(TagRule.isMovingTag("alpine"));
    assertTrue(TagRule.isMovingTag("clamav-latest"));
    assertTrue(TagRule.isMovingTag("stable-alpine"));
    assertTrue(TagRule.isMovingTag("release-11"));
    assertTrue(TagRule.isMovingTag("2.4.0-latest"));
  }
}
