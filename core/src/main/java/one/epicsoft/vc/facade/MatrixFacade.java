package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.MatrixCellDto;
import one.epicsoft.vc.dto.MatrixDto;
import one.epicsoft.vc.dto.MatrixRowDto;
import one.epicsoft.vc.enums.MatrixCellState;
import one.epicsoft.vc.enums.VersionStrategy;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentEntity;
import one.epicsoft.vc.jpa.entity.EnvironmentEntity;
import one.epicsoft.vc.mapper.EnvironmentMapper;
import one.epicsoft.vc.service.ComponentEnvironmentService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.EnvironmentService;
import one.epicsoft.vc.service.ProjectService;
import one.epicsoft.vc.util.VcUtil;
import one.epicsoft.vc.version.VersionComparison;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * Builds the version overview of one project.
 *
 * <p>Three queries, then assembly in memory: at this size — a few dozen
 * components across three environments — anything cleverer would cost more to
 * read than it saves to run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatrixFacade {

  private final ProjectService projectService;
  private final EnvironmentService environmentService;
  private final ComponentService componentService;
  private final ComponentEnvironmentService componentEnvironmentService;
  private final EnvironmentMapper environmentMapper;

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public MatrixDto getByProject(@Nonnull final UUID projectId) {
    // Reading it is what turns an unknown id into a 404 instead of an empty matrix.
    this.projectService.getById(projectId);

    final List<EnvironmentEntity> environments = this.environmentService.findAllByProject(projectId);
    final List<ComponentEntity> components = this.componentService.findAllByProject(projectId);
    final Map<UUID, Map<UUID, ComponentEnvironmentEntity>> cells =
        this.componentEnvironmentService.findAllByProject(projectId).stream()
            .collect(Collectors.groupingBy(ComponentEnvironmentEntity::getComponentId,
                                           Collectors.toMap(ComponentEnvironmentEntity::getEnvironmentId,
                                                            Function.identity())));

    final MatrixDto matrix = new MatrixDto();
    matrix.setProjectId(projectId);
    matrix.setEnvironments(environments.stream().map(this.environmentMapper::toDto).toList());
    matrix.setRows(components.stream()
                             .map(component -> this.toRow(component, environments,
                                                          cells.getOrDefault(component.getId(), Map.of())))
                             .toList());
    return matrix;
  }

  @Nonnull
  private MatrixRowDto toRow(@Nonnull final ComponentEntity component,
                             @Nonnull final List<EnvironmentEntity> environments,
                             @Nonnull final Map<UUID, ComponentEnvironmentEntity> cellsByEnvironment) {
    final VersionStrategy strategy = component.getVersionStrategy();
    final List<String> versions = cellsByEnvironment.values().stream()
                                                    .filter(ComponentEnvironmentEntity::isActive)
                                                    .map(ComponentEnvironmentEntity::getCurrentVersion)
                                                    .filter(VcUtil::nonBlank)
                                                    .toList();
    // One version everywhere is the normal case; two are the reason this view exists. A Debian package built for
    // another distribution is the same version.
    final boolean deviating = versions.stream().anyMatch(version -> VersionComparison.differs(versions.getFirst(), version, strategy));
    final String reference = deviating ? this.referenceVersion(environments, cellsByEnvironment) : null;

    final MatrixRowDto row = new MatrixRowDto();
    row.setComponentId(component.getId());
    row.setComponentName(component.getName());
    row.setSourceType(component.getSourceType());
    row.setDeviating(deviating);
    row.setCells(environments.stream()
                             .map(environment -> this.toCell(environment, cellsByEnvironment.get(environment.getId()),
                                                             reference, strategy))
                             .toList());
    return row;
  }

  /**
   * What the other environments are compared against: what is live. Production
   * first, and where none is recorded there, the first column that has a version
   * — marking every cell of a row that differs somewhere would highlight nothing.
   */
  @Nullable
  private String referenceVersion(@Nonnull final List<EnvironmentEntity> environments,
                                  @Nonnull final Map<UUID, ComponentEnvironmentEntity> cellsByEnvironment) {
    return environments.stream()
                       .sorted(Comparator.comparing(EnvironmentEntity::isProduction).reversed())
                       .map(environment -> cellsByEnvironment.get(environment.getId()))
                       .filter(Objects::nonNull)
                       .filter(ComponentEnvironmentEntity::isActive)
                       .map(ComponentEnvironmentEntity::getCurrentVersion)
                       .filter(VcUtil::nonBlank)
                       .findFirst()
                       .orElse(null);
  }

  @Nonnull
  private MatrixCellDto toCell(@Nonnull final EnvironmentEntity environment,
                               @Nullable final ComponentEnvironmentEntity cell,
                               @Nullable final String reference,
                               @Nullable final VersionStrategy strategy) {
    final MatrixCellDto dto = new MatrixCellDto();
    dto.setEnvironmentId(environment.getId());
    dto.setMovingTag(false);
    dto.setDeviating(false);

    if (isNull(cell)) {
      dto.setState(MatrixCellState.NOT_RECORDED);
      return dto;
    }
    dto.setComponentEnvironmentId(cell.getId());
    dto.setState(this.toState(cell));
    if (!cell.isActive()) {
      // Switched off means neutral: the version it carried before is history, and
      // a warning about a moving tag on something that does not run here would be
      // a warning nobody can act on.
      return dto;
    }
    dto.setCurrentVersion(cell.getCurrentVersion());
    dto.setLastChangedAt(cell.getLastChangedAt());
    dto.setPlannedVersion(cell.getPlannedVersion());
    dto.setPlannedFor(cell.getPlannedFor());
    dto.setMovingTag(cell.isMovingTag());
    dto.setDeviating(nonNull(reference) && nonBlank(cell.getCurrentVersion())
                     && VersionComparison.differs(reference, cell.getCurrentVersion(), strategy));
    return dto;
  }

  @Nonnull
  private MatrixCellState toState(@Nonnull final ComponentEnvironmentEntity cell) {
    if (!cell.isActive()) {
      return MatrixCellState.INACTIVE;
    }
    return cell.isVersionUnknown() ? MatrixCellState.UNKNOWN : MatrixCellState.RECORDED;
  }
}
