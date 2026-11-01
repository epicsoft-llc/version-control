package one.epicsoft.vc.config;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vc.links")
public class LinkProperties {

  @Nonnull
  private Duration checkInterval = Duration.ofDays(7);
}
