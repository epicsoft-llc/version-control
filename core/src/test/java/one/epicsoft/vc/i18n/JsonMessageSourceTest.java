package one.epicsoft.vc.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.util.List;
import java.util.Locale;

import static one.epicsoft.vc.util.VcUtil.nonBlank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(OutputCaptureExtension.class)
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
  void returnsNoMessageForAnUnknownKey(final CapturedOutput output) {
    // Loud in the log, absent in the response: a raw key in the user interface
    // reads like a defect to whoever sees it.
    assertNull(this.source.getMessage("error.doesNotExist", null, null, Locale.ENGLISH));
    assertTrue(output.getAll().contains("No message for key 'error.doesNotExist'"));
  }

  @Test
  void staysQuietWhenTheCallerBringsATextOfItsOwn(final CapturedOutput output) {
    // How the validator asks for the Jakarta keys before it falls back on its own translation.
    assertEquals("own text", this.source.getMessage("jakarta.validation.constraints.NotNull.message", null, "own text", Locale.GERMAN));
    assertFalse(output.getAll().contains("No message for key"));
  }
}
