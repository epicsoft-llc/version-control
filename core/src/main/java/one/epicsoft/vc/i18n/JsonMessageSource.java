package one.epicsoft.vc.i18n;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * Message source over the flat translation files {@code messages/<language>.json}.
 *
 * <p>The files are flat by design: one key per line, the dots belong to the key.
 * A nested file would silently resolve nothing here, so {@code verifyTranslations}
 * refuses one at build time.
 */
@Slf4j
public class JsonMessageSource extends AbstractMessageSource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Map<language, Map<key, text>>
  private final Map<String, Map<String, String>> messages = new HashMap<>();

  private final String fallbackLanguage;

  public JsonMessageSource(@Nonnull final List<String> languages, @Nonnull final String fallbackLanguage) {
    this.fallbackLanguage = fallbackLanguage;
    languages.forEach(this::load);
  }

  @Nullable
  @Override
  protected MessageFormat resolveCode(@Nonnull final String code, @Nonnull final Locale locale) {
    final String pattern = this.resolve(code, locale.getLanguage());
    if (nonBlank(pattern)) {
      return new MessageFormat(pattern, locale);
    }
    // Loud on the server, quiet in the response: a key leaking into the user
    // interface reads like a defect to whoever sees it.
    log.error("No message for key '{}' in language '{}' or fallback '{}'", code, locale.getLanguage(), this.fallbackLanguage);
    return null;
  }

  @Nullable
  private String resolve(@Nonnull final String code, @Nonnull final String language) {
    final String direct = this.messages.getOrDefault(language, Map.of()).get(code);
    if (nonBlank(direct)) {
      return direct;
    }
    return this.messages.getOrDefault(this.fallbackLanguage, Map.of()).get(code);
  }

  private void load(@Nonnull final String language) {
    final ClassPathResource resource = new ClassPathResource("messages/%s.json".formatted(language));
    final Map<String, String> loaded = new HashMap<>();
    try {
      MAPPER.readTree(resource.getContentAsByteArray())
            .properties()
            .forEach(entry -> loaded.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText()));
    } catch (final IOException e) {
      throw new UncheckedIOException("Cannot read messages for language '%s'".formatted(language), e);
    }
    log.info("Loaded {} messages for language '{}'", loaded.size(), language);
    this.messages.put(language, loaded);
  }
}
