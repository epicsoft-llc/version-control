package one.epicsoft.vc.service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.jpa.entity.ComponentChangeEntity;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.repository.ComponentChangeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentChangeService {

  private final ComponentChangeRepository componentChangeRepository;

  @Nonnull
  public ComponentChangeEntity record(@Nonnull final ComponentEntity component,
                                      @Nonnull final Instant changedAt,
                                      @Nonnull final String field,
                                      @Nullable final String oldValue,
                                      @Nullable final String newValue,
                                      @Nullable final String note) {
    final ComponentChangeEntity change = new ComponentChangeEntity();
    change.setComponent(component);
    change.setChangedAt(changedAt);
    change.setField(field);
    change.setOldValue(oldValue);
    change.setNewValue(newValue);
    change.setNote(note);
    return this.componentChangeRepository.save(change);
  }

  @Nonnull
  public List<ComponentChangeEntity> findAllByComponent(@Nonnull final UUID componentId) {
    return this.componentChangeRepository.findAllByComponentIdOrderByChangedAtDesc(componentId);
  }

  @Nonnull
  public List<ComponentChangeEntity> findRecentByProject(@Nonnull final UUID projectId, final int limit) {
    return this.componentChangeRepository.findRecentByProject(projectId, PageRequest.of(0, limit));
  }
}
