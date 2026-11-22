package one.epicsoft.vc.service;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.exception.AppNotFoundException;
import one.epicsoft.vc.jpa.entity.VersionChangeEntity;
import one.epicsoft.vc.jpa.repository.VersionChangeRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionChangeService {

  private final VersionChangeRepository versionChangeRepository;

  @Nonnull
  public VersionChangeEntity getById(@Nonnull final UUID id) {
    return this.versionChangeRepository.findById(id)
                                       .orElseThrow(() -> AppNotFoundException.byId("VersionChange", id));
  }

  @Nonnull
  public List<VersionChangeEntity> findAllByComponentEnvironment(@Nonnull final UUID componentEnvironmentId) {
    return this.versionChangeRepository.findAllByComponentEnvironment(componentEnvironmentId);
  }

  @Nonnull
  public List<VersionChangeEntity> findAllByComponent(@Nonnull final UUID componentId) {
    return this.versionChangeRepository.findAllByComponent(componentId);
  }

  @Nonnull
  public List<VersionChangeEntity> findAllByProject(@Nonnull final UUID projectId) {
    return this.versionChangeRepository.findAllByProject(projectId);
  }

  @Nonnull
  public VersionChangeEntity save(@Nonnull final VersionChangeEntity entity) {
    return this.versionChangeRepository.save(entity);
  }

  /** Flushed so the update time a correction sets is already in the answer. */
  @Nonnull
  public VersionChangeEntity saveAndFlush(@Nonnull final VersionChangeEntity entity) {
    return this.versionChangeRepository.saveAndFlush(entity);
  }
}
