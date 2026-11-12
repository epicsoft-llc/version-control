package one.epicsoft.vc.job;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.facade.ComponentLinkFacade;
import one.epicsoft.vc.facade.LinkSearchFacade;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.LinkSearchEntity;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.LinkSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.nonNull;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vc.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class LinkSearchJob {

  private final ComponentService componentService;
  private final LinkSearchService linkSearchService;
  private final LinkSearchFacade linkSearchFacade;
  private final ComponentLinkFacade componentLinkFacade;

  @Scheduled(fixedDelayString = "${vc.scheduler.tick-interval}")
  public void searchNextDueComponent() {
    final UUID componentId = this.nextComponent();
    if (nonNull(componentId)) {
      try {
        this.linkSearchFacade.search(componentId);
      } catch (final RuntimeException exception) {
        // Without moving the row on, this component would head the queue on every tick and hold up the rest.
        log.error("Link search for component {} ended unexpectedly", componentId, exception);
        this.componentLinkFacade.postponeAfterUnexpectedFailure(componentId);
      }
    }
  }

  @Nullable
  private UUID nextComponent() {
    final List<ComponentEntity> neverSearched = this.componentService.findWithoutLinkSearch(1);
    if (!neverSearched.isEmpty()) {
      return neverSearched.getFirst().getId();
    }
    final List<LinkSearchEntity> due = this.linkSearchService.findDue(Instant.now(), 1);
    return due.isEmpty() ? null : due.getFirst().getComponentId();
  }
}
