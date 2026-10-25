package one.epicsoft.vc.i18n;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Locale;

import static one.epicsoft.vc.util.VcUtil.nonBlank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonMessageSourceTest {

  private final JsonMessageSource source = new JsonMessageSource(List.of("en", "de"), "en");

  // The texts change without this code changing, so the assertions are about
  // behaviour, never about wording.

  @Test
  void resolvesTheRequestedLanguage() {
    final String german = this.source.getMessage("error.notFound", null, Locale.GERMAN);
    final String english = this.source.getMessage("error.notFound", null, Locale.ENGLISH);

    assertTrue(nonBlank(german));
    assertNotEquals(german, english);
  }

  @Test
  void fallsBackToTheDefaultLanguage() {
    // French is not loaded at all — the reader still gets a sentence, not a key.
    assertEquals(this.source.getMessage("error.notFound", null, Locale.ENGLISH),
                 this.source.getMessage("error.notFound", null, Locale.FRENCH));
  }

  @Test
  void returnsNoMessageForAnUnknownKey() {
    // Loud in the log, absent in the response: a raw key in the user interface
    // reads like a defect to whoever sees it.
    assertNull(this.source.getMessage("error.doesNotExist", null, null, Locale.ENGLISH));
  }
}
