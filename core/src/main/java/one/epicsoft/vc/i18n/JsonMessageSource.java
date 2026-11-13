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
    return nonBlank(pattern) ? new MessageFormat(pattern, locale) : null;
  }

  /**
   * Reached only when the caller has no text of its own, so the key would reach the reader — loud for that reason. A
   * caller with one expects the miss: the validator offers every Jakarta key here first, then uses its own translation.
   */
  @Nullable
  @Override
  protected String getDefaultMessage(@Nonnull final String code) {
    log.error("No message for key '{}', not even in the fallback language '{}'", code, this.fallbackLanguage);
    return super.getDefaultMessage(code);
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
