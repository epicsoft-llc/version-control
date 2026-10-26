package one.epicsoft.vc.service;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.jpa.entity.EolStatusEntity;
import one.epicsoft.vc.jpa.repository.EolStatusRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EolStatusService {

  private final EolStatusRepository eolStatusRepository;

  @Nonnull
  public Optional<EolStatusEntity> findByComponent(@Nonnull final UUID componentId) {
    return this.eolStatusRepository.findByComponentId(componentId);
  }

  @Nonnull
  public List<EolStatusEntity> findAllByProject(@Nonnull final UUID projectId) {
    return this.eolStatusRepository.findAllByProject(projectId);
  }

  /** The status rows whose next lookup is due, longest overdue first. */
  @Nonnull
  public List<EolStatusEntity> findDue(@Nonnull final Instant now, final int limit) {
    return this.eolStatusRepository.findDue(now, PageRequest.of(0, limit));
  }

  @Nonnull
  public EolStatusEntity save(@Nonnull final EolStatusEntity entity) {
    return this.eolStatusRepository.save(entity);
  }

  public void deleteByComponent(@Nonnull final UUID componentId) {
    this.eolStatusRepository.deleteByComponentId(componentId);
  }
}
