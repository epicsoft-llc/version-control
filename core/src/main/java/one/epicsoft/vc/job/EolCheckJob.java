package one.epicsoft.vc.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.facade.EolStatusFacade;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.EolStatusEntity;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.EolStatusService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Looks up one support date per tick.
 *
 * <p>Shares the tick of the update check but not its cycle: a support date moves
 * slowly and is asked about weekly. Components that name a product but have never
 * been looked up come first — nothing at all is worse than something stale.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vc.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class EolCheckJob {

  private final ComponentService componentService;
  private final EolStatusService eolStatusService;
  private final EolStatusFacade eolStatusFacade;

  @Scheduled(fixedDelayString = "${vc.scheduler.tick-interval}")
  public void checkNextDueComponent() {
    final UUID componentId = this.nextComponent();
    if (componentId == null) {
      return;
    }
    try {
      this.eolStatusFacade.check(componentId);
    } catch (final RuntimeException exception) {
      // Its transaction rolled back, and the new due date with it; without moving
      // the row on, this component would be first in the queue on every tick.
      log.error("End-of-life lookup for component {} ended unexpectedly", componentId, exception);
      this.eolStatusFacade.postponeAfterUnexpectedFailure(componentId);
    }
  }

  private UUID nextComponent() {
    final List<ComponentEntity> neverChecked = this.componentService.findWithoutEolStatus(1);
    if (!neverChecked.isEmpty()) {
      return neverChecked.getFirst().getId();
    }
    final List<EolStatusEntity> due = this.eolStatusService.findDue(Instant.now(), 1);
    return due.isEmpty() ? null : due.getFirst().getComponentId();
  }
}
