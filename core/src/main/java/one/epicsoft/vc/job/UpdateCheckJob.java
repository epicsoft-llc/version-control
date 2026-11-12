package one.epicsoft.vc.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.facade.UpdateCheckFacade;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.service.ComponentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

/**
 * Checks one component per tick, the longest overdue first.
 *
 * <p>Deliberately not a nightly sweep of everything. A short tick that takes a
 * single component spreads the requests over the day, survives a restart without
 * losing a night, and makes a failing source cost one component rather than the
 * whole run. With twenty components checked every two days this is a few
 * requests an hour at most.
 *
 * <p>The job holds no logic of its own: due dates, back-off and the minimum
 * interval all live in the facade.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vc.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class UpdateCheckJob {

  private final ComponentService componentService;
  private final UpdateCheckFacade updateCheckFacade;

  @Scheduled(fixedDelayString = "${vc.scheduler.tick-interval}")
  public void checkNextDueComponent() {
    final List<ComponentEntity> due = this.componentService.findDue(Instant.now(), 1);
    if (due.isEmpty()) {
      return;
    }
    final ComponentEntity component = due.getFirst();
    log.info("Checking component {} ({})", component.getName(), component.getId());
    try {
      this.updateCheckFacade.check(component.getId());
    } catch (final RuntimeException exception) {
      // The facade turns an unreachable source into a recorded error itself. What
      // reaches this point is a defect, and swallowing it would stop the tick for
      // every other component as well.
      log.error("Check of component {} ended unexpectedly", component.getId(), exception);
      // Its transaction rolled back, and the new due date with it, so without
      // this the same component is first in the queue again on the next tick and
      // no other component is ever reached.
      this.updateCheckFacade.postponeAfterUnexpectedFailure(component.getId());
    }
  }
}
