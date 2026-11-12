package one.epicsoft.vc.config;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

/** How often the scheduler asks about a component, and how long it waits after a failure. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vc.check")
public class CheckProperties {

  /** How long a component is left alone between two regular checks. */
  @Nonnull
  private Duration defaultInterval = Duration.ofHours(48);

  /** The floor for the scheduler and the rule preview; Check now is not bound by it. */
  @Nonnull
  private Duration minInterval = Duration.ofHours(48);

  /**
   * Spread, applied around the next due date. Without it the check times of all
   * components drift onto the same hour and stay there.
   */
  @Nonnull
  private Duration jitter = Duration.ofHours(2);

  /** The wait after the first, second and any further failure in a row. */
  @Nonnull
  private List<Duration> backoff = List.of(Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(12));

  /** How long a failing check is tolerated before it reaches the task list. */
  private int errorGraceDays = 3;
}
