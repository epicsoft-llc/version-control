package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.VersionChangeDto;
import one.epicsoft.vc.dto.VersionChangePackageRequest;
import one.epicsoft.vc.dto.VersionChangeRequest;
import one.epicsoft.vc.exception.AppValidationException;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentPackageEntity;
import one.epicsoft.vc.jpa.entity.ComponentPackageEntity;
import one.epicsoft.vc.jpa.entity.VersionChangeEntity;
import one.epicsoft.vc.mapper.VersionChangeMapper;
import one.epicsoft.vc.service.ComponentEnvironmentService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.ProjectService;
import one.epicsoft.vc.service.VersionChangeService;
import one.epicsoft.vc.service.VulnerabilityCheckService;
import one.epicsoft.vc.util.VcUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.isEquals;
import static one.epicsoft.vc.util.VcUtil.isTrue;
import static one.epicsoft.vc.util.VcUtil.nonBlank;
import static one.epicsoft.vc.util.VcUtil.trimmed;

/**
 * The one place a deployed version changes.
 *
 * <p>Updating the record and writing the history entry happen here, in a single
 * transaction. Nothing else may write a version — a second path would be the one
 * that leaves the history with holes, and those holes would be exactly the
 * upgrades that went badly enough to be done in a hurry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionChangeFacade {

  /** How the packages of one action are folded into a single version string. */
  private static final String PACKAGE_SEPARATOR = ", ";

  private final VersionChangeService versionChangeService;
  private final ComponentEnvironmentService componentEnvironmentService;
  private final ComponentService componentService;
  private final ProjectService projectService;
  private final VersionChangeMapper versionChangeMapper;
  private final ComponentEnvironmentFacade componentEnvironmentFacade;
  private final VulnerabilityCheckService vulnerabilityCheckService;

  /**
   * The three list methods read what they filter by first. An unknown or deleted
   * id would otherwise answer with an empty list, and "nothing was ever changed
   * here" is exactly the wrong thing to tell someone whose history is elsewhere.
   */
  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<VersionChangeDto> findAllByComponentEnvironment(@Nonnull final UUID componentEnvironmentId) {
    this.componentEnvironmentService.getById(componentEnvironmentId);
    return this.versionChangeMapper.toDtos(this.versionChangeService.findAllByComponentEnvironment(componentEnvironmentId));
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<VersionChangeDto> findAllByComponent(@Nonnull final UUID componentId) {
    this.componentService.getById(componentId);
    return this.versionChangeMapper.toDtos(this.versionChangeService.findAllByComponent(componentId));
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<VersionChangeDto> findAllByProject(@Nonnull final UUID projectId) {
    this.projectService.getById(projectId);
    return this.versionChangeMapper.toDtos(this.versionChangeService.findAllByProject(projectId));
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public VersionChangeDto getById(@Nonnull final UUID id) {
    return this.versionChangeMapper.toDto(this.versionChangeService.getById(id));
  }

  /**
   * Records a new version and the story around it.
   *
   * <p>A component with several packages sends one version per package and still
   * gets a single entry: the note, the backup and the rollback path describe the
   * maintenance action, not an individual package.
   */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public VersionChangeDto changeVersion(@Nonnull final VersionChangeRequest request) {
    final ComponentEnvironmentEntity cell =
        this.componentEnvironmentService.getById(request.getComponentEnvironmentId());
    if (!cell.isActive()) {
      // "Deliberately not present here" and "runs here with this version" cannot
      // both be true. Activating the cell is its own, visible step.
      throw AppValidationException.inactiveCell(cell.getId());
    }

    // Which shape is valid follows from the component, never from what the caller
    // happened to send: a package component that accepts a single version writes
    // that version into the cell while the package rows keep the old ones, and
    // the next summary then reports a change that never happened.
    final List<ComponentPackageEntity> configured = this.componentService.findPackages(cell.getComponentId());
    final List<VersionChangePackageRequest> packages =
        isNull(request.getPackages()) ? List.of() : request.getPackages();
    if (configured.isEmpty() && !packages.isEmpty()) {
      throw AppValidationException.parameterRequired("toVersion, because this component has no packages");
    }
    if (!configured.isEmpty() && packages.isEmpty()) {
      throw AppValidationException.parameterRequired("packages, because this component is maintained per package");
    }
    if (!configured.isEmpty() && nonBlank(request.getToVersion())) {
      // Silently dropping it would leave the caller believing it was applied.
      throw AppValidationException.parameterRequired("packages only, because this component is maintained per package");
    }

    final Instant changedAt = Instant.now();
    final VersionSummary summary = packages.isEmpty()
                                   ? this.applySingleVersion(cell, request)
                                   : this.applyPackageVersions(cell, configured, packages);

    cell.setLastChangedAt(changedAt);
    this.componentEnvironmentFacade.endPlanIfReached(cell);
    this.componentEnvironmentService.save(cell);

    final VersionChangeEntity entity = new VersionChangeEntity();
    entity.setComponentEnvironment(cell);
    entity.setFromVersion(summary.fromVersion());
    entity.setToVersion(summary.toVersion());
    entity.setChangedAt(changedAt);
    entity.setNote(request.getNote());
    entity.setBackupTaken(isTrue(request.getBackupTaken()));
    entity.setBackupRef(request.getBackupRef());
    entity.setRollbackPath(request.getRollbackPath());
    entity.setDowntimeMinutes(request.getDowntimeMinutes());
    entity.setIssues(request.getIssues());

    final VersionChangeEntity saved = this.versionChangeService.save(entity);
    this.vulnerabilityCheckService.markDue(cell.getComponentId());
    log.info("Recorded version change of component environment {}", cell.getId());
    return this.versionChangeMapper.toDto(saved);
  }

  @Nonnull
  private VersionSummary applySingleVersion(@Nonnull final ComponentEnvironmentEntity cell,
                                            @Nonnull final VersionChangeRequest request) {
    if (!nonBlank(request.getToVersion())) {
      throw AppValidationException.parameterRequired("toVersion or packages");
    }
    final String toVersion = trimmed(request.getToVersion());
    if (isEquals(toVersion, cell.getCurrentVersion())) {
      // An entry that records no change is noise in the one list that still has
      // to be readable years from now.
      throw AppValidationException.versionUnchanged(cell.getId(), toVersion);
    }

    final String fromVersion = cell.getCurrentVersion();
    cell.setCurrentVersion(toVersion);
    return new VersionSummary(fromVersion, toVersion);
  }

  /**
   * Applies one version per package and folds the changed ones into a single pair
   * of version strings, so that "docker-ce 29.7.1, containerd.io 2.3.3" becomes
   * "docker-ce 29.7.2, containerd.io 2.3.4". Packages that did not move stay out
   * of the summary; naming them would bury the ones that did.
   */
  @Nonnull
  private VersionSummary applyPackageVersions(@Nonnull final ComponentEnvironmentEntity cell,
                                              @Nonnull final List<ComponentPackageEntity> configured,
                                              @Nonnull final List<VersionChangePackageRequest> requested) {
    final Set<String> known = configured.stream().map(ComponentPackageEntity::getName).collect(Collectors.toSet());
    final Set<String> seen = new HashSet<>();
    final Map<String, String> targets = new LinkedHashMap<>();
    for (final VersionChangePackageRequest entry : requested) {
      final String name = trimmed(entry.getPackageName());
      if (!known.contains(name)) {
        // Otherwise a typo silently creates a package row nobody configured.
        throw AppValidationException.unknownPackage(name);
      }
      if (!seen.add(name)) {
        throw AppValidationException.duplicatePackage(name);
      }
      targets.put(name, trimmed(entry.getVersion()));
    }

    final Map<String, ComponentEnvironmentPackageEntity> existing =
        this.componentEnvironmentService.findPackages(cell.getId()).stream()
            .collect(Collectors.toMap(ComponentEnvironmentPackageEntity::getPackageName, Function.identity()));

    final List<String> before = new ArrayList<>();
    final List<String> after = new ArrayList<>();
    final List<ComponentEnvironmentPackageEntity> touched = new ArrayList<>();

    // The configured order, not the order of the request: the summary of one
    // component has to read the same way every time.
    for (final ComponentPackageEntity configuredPackage : configured) {
      final String name = configuredPackage.getName();
      final String target = targets.get(name);
      if (isNull(target)) {
        continue;
      }
      final ComponentEnvironmentPackageEntity entity = existing.get(name);
      final String current = nonNull(entity) ? entity.getCurrentVersion() : null;
      if (isEquals(target, current)) {
        continue;
      }

      final ComponentEnvironmentPackageEntity toSave = nonNull(entity) ? entity : this.newPackage(cell, name);
      toSave.setCurrentVersion(target);
      touched.add(toSave);
      if (nonBlank(current)) {
        before.add("%s %s".formatted(name, current));
      }
      after.add("%s %s".formatted(name, target));
    }

    if (touched.isEmpty()) {
      throw AppValidationException.versionUnchanged(cell.getId(), String.join(PACKAGE_SEPARATOR, targets.values()));
    }
    this.componentEnvironmentService.savePackages(touched);
    this.applyLeadingVersion(cell, configured, targets);

    return new VersionSummary(before.isEmpty() ? null : String.join(PACKAGE_SEPARATOR, before),
                              String.join(PACKAGE_SEPARATOR, after));
  }

  /**
   * The matrix cell shows the leading package. Without one configured the
   * recorded version stays as it is — a wrong number there is worse than none.
   */
  private void applyLeadingVersion(@Nonnull final ComponentEnvironmentEntity cell,
                                   @Nonnull final List<ComponentPackageEntity> configured,
                                   @Nonnull final Map<String, String> targets) {
    configured.stream()
              .filter(ComponentPackageEntity::isLeadingPackage)
              .map(ComponentPackageEntity::getName)
              .map(targets::get)
              .filter(VcUtil::nonBlank)
              .findFirst()
              .ifPresent(cell::setCurrentVersion);
  }

  @Nonnull
  private ComponentEnvironmentPackageEntity newPackage(@Nonnull final ComponentEnvironmentEntity cell,
                                                       @Nonnull final String name) {
    final ComponentEnvironmentPackageEntity entity = new ComponentEnvironmentPackageEntity();
    entity.setComponentEnvironment(cell);
    entity.setPackageName(name);
    return entity;
  }

  /** What the history entry records, whichever way the versions arrived. */
  private record VersionSummary(@Nullable String fromVersion, @Nonnull String toVersion) {
  }
}
