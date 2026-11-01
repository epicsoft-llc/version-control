package one.epicsoft.vc.service;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import one.epicsoft.vc.enums.LinkOrigin;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentLinkEntity;
import one.epicsoft.vc.jpa.repository.ComponentLinkRepository;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComponentLinkService {

  private final ComponentLinkRepository componentLinkRepository;

  @Nonnull
  public List<ComponentLinkEntity> findAllByComponents(@Nonnull final Collection<UUID> componentIds) {
    if (componentIds.isEmpty()) {
      return List.of();
    }
    return this.componentLinkRepository.findAllByComponentIdInOrderBySortOrderAsc(componentIds);
  }

  @Nonnull
  public List<ComponentLinkEntity> findAllByComponent(@Nonnull final UUID componentId, @Nonnull final LinkOrigin origin) {
    return this.componentLinkRepository.findAllByComponentIdAndOriginOrderBySortOrderAsc(componentId, origin);
  }

  @Nonnull
  public List<ComponentLinkEntity> replace(@Nonnull final ComponentEntity component,
                                           @Nonnull final Collection<LinkOrigin> origins,
                                           @Nonnull final List<ComponentLinkEntity> links) {
    this.componentLinkRepository.deleteByComponentAndOrigins(component.getId(), origins);
    // Inserts go before deletes inside one transaction; an address that survives would collide with its old row.
    this.componentLinkRepository.flush();
    links.forEach(link -> link.setComponent(component));
    return this.componentLinkRepository.saveAll(links);
  }
}
