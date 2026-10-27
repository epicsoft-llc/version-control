package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.ComponentEnvironmentDto;
import one.epicsoft.vc.dto.ComponentEnvironmentPackageDto;
import one.epicsoft.vc.dto.ComponentEnvironmentRequest;
import one.epicsoft.vc.dto.RolloutPlanRequest;
import one.epicsoft.vc.exception.AppValidationException;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentPackageEntity;
import one.epicsoft.vc.jpa.entity.EnvironmentEntity;
import one.epicsoft.vc.mapper.ComponentEnvironmentMapper;
import one.epicsoft.vc.service.ComponentEnvironmentService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.EnvironmentService;
import one.epicsoft.vc.service.VulnerabilityCheckService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.isEquals;
import static one.epicsoft.vc.util.VcUtil.nonBlank;
import static one.epicsoft.vc.util.VcUtil.nonEquals;
import static one.epicsoft.vc.util.VcUtil.trimmed;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentEnvironmentFacade {

  private final ComponentEnvironmentService componentEnvironmentService;
  private final ComponentService componentService;
  private final EnvironmentService environmentService;
  private final ComponentEnvironmentMapper componentEnvironmentMapper;
  private final ComponentChangeFacade componentChangeFacade;
  private final VulnerabilityCheckService vulnerabilityCheckService;

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<ComponentEnvironmentDto> findAllByComponent(@Nonnull final UUID componentId) {
    return this.toDtos(this.componentEnvironmentService.findAllByComponent(componentId));
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<ComponentEnvironmentDto> findAllByProject(@Nonnull final UUID projectId) {
    return this.toDtos(this.componentEnvironmentService.findAllByProject(projectId));
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public ComponentEnvironmentDto getById(@Nonnull final UUID id) {
    final ComponentEnvironmentEntity entity = this.componentEnvironmentService.getById(id);
    return this.toDtos(List.of(entity)).getFirst();
  }

  /**
   * Creates the record or updates the existing one for that pair.
   *
   * <p>A version may be recorded here while none is known yet. Changing a version
   * that is already there is refused: that belongs in the dialog which writes the
   * history entry in the same step, and a second way in is exactly how history
   * ends up with holes.
   */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public ComponentEnvironmentDto createOrUpdate(@Nonnull final ComponentEnvironmentRequest request) {
    final ComponentEntity component = this.componentService.getById(request.getComponentId());
    final EnvironmentEntity environment = this.environmentService.getById(request.getEnvironmentId());
    if (nonEquals(component.getProjectId(), environment.getProjectId())) {
      // Otherwise the matrix of one project shows a column it cannot resolve.
      throw AppValidationException.differentProjects(component.getId(), environment.getId());
    }

    final ComponentEnvironmentEntity entity = this.componentEnvironmentService
        .findByComponentAndEnvironment(component.getId(), environment.getId())
        .orElseGet(() -> {
          final ComponentEnvironmentEntity created = new ComponentEnvironmentEntity();
          created.setComponent(component);
          created.setEnvironment(environment);
          // Active from the start, and set here rather than left to @PrePersist:
          // the guards below read the flag long before anything is written.
          created.setActive(Boolean.TRUE);
          return created;
        });

    if (nonNull(request.getActive())) {
      entity.setActive(request.getActive());
    }
    if (nonBlank(request.getCurrentVersion())) {
      this.recordFirstVersion(entity, request.getCurrentVersion());
    }
    final ComponentEnvironmentEntity saved = this.componentEnvironmentService.save(entity);
    this.vulnerabilityCheckService.markDue(component.getId());
    return this.toDtos(List.of(saved)).getFirst();
  }

  @Transactional(rollbackFor = Throwable.class)
  public void delete(@Nonnull final UUID id) {
    this.componentEnvironmentService.delete(this.componentEnvironmentService.getById(id));
  }

  /** Plans a rollout for one environment; a plan already there is replaced. */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public ComponentEnvironmentDto plan(@Nonnull final UUID id, @Nonnull final RolloutPlanRequest request) {
    final ComponentEnvironmentEntity cell = this.componentEnvironmentService.getById(id);
    if (!cell.isActive()) {
      // A rollout into an environment the component deliberately stays out of is a contradiction.
      throw AppValidationException.inactiveCell(cell.getId());
    }
    final String version = trimmed(request.getPlannedVersion());
    if (isEquals(version, cell.getCurrentVersion())) {
      throw AppValidationException.versionUnchanged(cell.getId(), version);
    }
    final String before = ComponentChangeFacade.planText(cell);
    cell.setPlannedVersion(version);
    cell.setPlannedFor(request.getPlannedFor());
    cell.setPlannedNote(nonBlank(request.getNote()) ? request.getNote().trim() : null);
    final ComponentEnvironmentEntity saved = this.componentEnvironmentService.save(cell);
    this.componentChangeFacade.recordPlan(saved, before);
    return this.toDtos(List.of(saved)).getFirst();
  }

  /** Drops the plan of a cell; a cell without one answers as it is. */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public ComponentEnvironmentDto unplan(@Nonnull final UUID id) {
    final ComponentEnvironmentEntity cell = this.componentEnvironmentService.getById(id);
    if (cell.hasPlan()) {
      final String before = ComponentChangeFacade.planText(cell);
      cell.clearPlan();
      this.componentEnvironmentService.save(cell);
      this.componentChangeFacade.recordPlan(cell, before);
    }
    return this.toDtos(List.of(cell)).getFirst();
  }

  /**
   * Ends a plan once its version, or a newer one, is recorded. Called on both ways a version gets into a cell,
   * so neither leaves a plan behind that has already happened.
   */
  @Transactional(rollbackFor = Throwable.class)
  public void endPlanIfReached(@Nonnull final ComponentEnvironmentEntity cell) {
    if (cell.isPlanReached(cell.getComponent().getVersionStrategy())) {
      final String before = ComponentChangeFacade.planText(cell);
      cell.clearPlan();
      this.componentChangeFacade.recordPlan(cell, before);
    }
  }

  private void recordFirstVersion(@Nonnull final ComponentEnvironmentEntity entity, @Nonnull final String version) {
    if (nonBlank(entity.getCurrentVersion())) {
      if (nonEquals(entity.getCurrentVersion(), version)) {
        throw AppValidationException.versionNeedsHistory(entity.getId());
      }
      // Echoing the unchanged version — the natural shape of this payload when
      // only `active` is toggled — must not move the timestamp away from the
      // deployment it records.
      return;
    }
    if (!entity.isActive()) {
      // The same invariant the version dialog enforces: a cell marked as
      // deliberately absent cannot also carry a deployed version. Switching an
      // existing cell off keeps the version it had — that one is history.
      throw AppValidationException.inactiveCell(entity.getComponentId(), entity.getEnvironmentId());
    }
    entity.setCurrentVersion(version);
    entity.setLastChangedAt(Instant.now());
    this.endPlanIfReached(entity);
  }

  /**
   * Loads the packages of every record in one query instead of one per row. The
   * matrix of a mid sized project is a few hundred rows, and almost none of them
   * have packages at all.
   */
  @Nonnull
  private List<ComponentEnvironmentDto> toDtos(@Nonnull final List<ComponentEnvironmentEntity> entities) {
    final List<UUID> ids = entities.stream().map(ComponentEnvironmentEntity::getId).toList();
    final Map<UUID, List<ComponentEnvironmentPackageEntity>> packages =
        this.componentEnvironmentService.findPackages(ids).stream()
            .collect(Collectors.groupingBy(ComponentEnvironmentPackageEntity::getComponentEnvironmentId));

    return entities.stream().map(entity -> {
      final ComponentEnvironmentDto dto = this.componentEnvironmentMapper.toDto(entity);
      final List<ComponentEnvironmentPackageDto> entries =
          this.componentEnvironmentMapper.toPackageDtos(packages.getOrDefault(entity.getId(), List.of()));
      dto.setPackages(entries);
      return dto;
    }).toList();
  }
}
