package one.epicsoft.vc.config;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import one.epicsoft.vc.enums.DebianVersionDisplay;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * How versions are written for a reader, in the dashboard and in the daily notification.
 *
 * <p>Only the display: what is stored, compared and copied is always the version as it was recorded.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vc.display")
public class DisplayProperties {

  /** Short unless asked otherwise: epoch and distribution repeat on every line and change nothing a reader decides. */
  @Nonnull
  private DebianVersionDisplay debianVersions = DebianVersionDisplay.SHORT;
}
