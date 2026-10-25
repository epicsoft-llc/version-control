package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.ReleaseLinkDto;
import one.epicsoft.vc.dto.UpgradeProposalDto;
import one.epicsoft.vc.dto.UpgradeStopDto;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.enums.VersionStrategy;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentEntity;
import one.epicsoft.vc.jpa.entity.UpdateCheckEntity;
import one.epicsoft.vc.jpa.entity.UpgradeStopEntity;
import one.epicsoft.vc.mapper.UpgradeStopMapper;
import one.epicsoft.vc.service.ComponentEnvironmentService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.UpdateCheckService;
import one.epicsoft.vc.service.UpgradeStopService;
import one.epicsoft.vc.version.AllowedCandidates;
import one.epicsoft.vc.version.UpgradePathCalculator;
import one.epicsoft.vc.version.UpgradeTarget;
import one.epicsoft.vc.version.VersionComparison;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * What to do next with one component, per environment.
 *
 * <p>The proposal is never "the newest version". It is the highest version the
 * step limit allows from where this environment stands, together with the
 * versions that cannot be skipped on the way, what the component says about
 * upgrading it, and whether it needs a maintenance window — everything somebody
 * needs before starting, in one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradeProposalFacade {

  private final ComponentService componentService;
  private final ComponentEnvironmentService componentEnvironmentService;
  private final UpdateCheckService updateCheckService;
  private final UpgradeStopService upgradeStopService;
  private final UpgradePathCalculator upgradePathCalculator;
  private final UpgradeStopMapper upgradeStopMapper;
  private final AllowedCandidates allowedCandidates;
  private final DependencyFacade dependencyFacade;

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<UpgradeProposalDto> getByComponent(@Nonnull final UUID componentId) {
    final ComponentEntity component = this.componentService.getById(componentId);
    final Optional<UpdateCheckEntity> check = this.updateCheckService.findByComponent(componentId);
    final List<UpgradeStopEntity> stops = this.upgradeStopService.findAllByComponent(componentId);
    // Read once for the whole project rather than per environment: the forward
    // check asks the same handful of rows for every one of them.
    final DependencyFacade.DependencyIndex index = this.dependencyFacade.index(component.getProjectId());

    return this.componentEnvironmentService.findAllByComponent(componentId).stream()
               .filter(ComponentEnvironmentEntity::isActive)
               .sorted(Comparator.comparing(cell -> cell.getEnvironment().getSortOrder()))
               // The candidates go through the rule, per environment: the stored
               // list is what the source offered, and proposing straight from it
               // would name variants and prereleases the check itself rejected.
               .map(cell -> this.toProposal(index, component, cell,
                                            check.map(entity -> this.allowedCandidates
                                                          .of(component, entity, cell.getCurrentVersion()))
                                                 .orElse(List.of()),
                                            stops))
               .toList();
  }

  @Nonnull
  private UpgradeProposalDto toProposal(@Nonnull final DependencyFacade.DependencyIndex index,
                                        @Nonnull final ComponentEntity component,
                                        @Nonnull final ComponentEnvironmentEntity cell,
                                        @Nonnull final List<String> candidates,
                                        @Nonnull final List<UpgradeStopEntity> stops) {
    final UpgradeTarget target = this.upgradePathCalculator.calculate(cell.getCurrentVersion(), candidates,
                                                                      component.getMaxStep(),
                                                                      component.getVersionStrategy());
    final UpgradeProposalDto proposal = new UpgradeProposalDto();
    proposal.setEnvironmentId(cell.getEnvironmentId());
    proposal.setEnvironmentName(cell.getEnvironment().getName());
    proposal.setCurrentVersion(cell.getCurrentVersion());
    proposal.setTarget(target.target());
    proposal.setBeyond(target.beyond());
    proposal.setUpgradePathNotes(component.getUpgradePathNotes());
    proposal.setUpgradeDocUrl(component.getUpgradeDocUrl());
    proposal.setNeedsMaintenanceWindow(component.isNeedsMaintenanceWindow());
    proposal.setStops(this.stopsBetween(stops, cell.getCurrentVersion(), target, component));
    proposal.setReleases(releasesBetween(candidates, cell.getCurrentVersion(), target, component));
    // Forwards: would this target break something that needs this component? If
    // so it is reported as blocked rather than available, and the entries name
    // what blocks it and which range it violates. Answering that question is
    // where this beats a table.
    proposal.setBlockedBy(this.dependencyFacade.blockers(index, component.getId(), cell.getEnvironmentId(),
                                                        target.target()));
    return proposal;
  }

  /**
   * The stops that lie between here and the proposed target.
   *
   * <p>Where nothing is proposed the stops are measured against what exists
   * beyond the limit instead: somebody planning that jump is exactly who needs to
   * know which versions they cannot skip.
   */
  @Nonnull
  private List<UpgradeStopDto> stopsBetween(@Nonnull final List<UpgradeStopEntity> stops,
                                            final String current,
                                            @Nonnull final UpgradeTarget target,
                                            @Nonnull final ComponentEntity component) {
    final String upper = nonBlank(target.target()) ? target.target() : target.beyond();
    if (!nonBlank(current) || !nonBlank(upper)) {
      return List.of();
    }
    return this.upgradeStopMapper.toDtos(
        stops.stream()
             .filter(stop -> this.between(stop.getVersion(), current, upper, component))
             .toList());
  }

  /**
   * Every version after the running one up to the target, oldest first, with its release notes. Reading those
   * for breaking changes is the slow part of every update; here they are one click each.
   */
  @Nonnull
  private static List<ReleaseLinkDto> releasesBetween(@Nonnull final List<String> candidates,
                                                      final String current,
                                                      @Nonnull final UpgradeTarget target,
                                                      @Nonnull final ComponentEntity component) {
    final String upper = nonBlank(target.target()) ? target.target() : target.beyond();
    if (!nonBlank(current) || !nonBlank(upper)) {
      return List.of();
    }
    final VersionStrategy strategy = component.getVersionStrategy();
    // The candidates come newest first; a version nothing can order is left out rather than guessed into place.
    return candidates.reversed().stream()
                     .filter(version -> VersionComparison.isBehind(current, version, strategy)
                                        && !VersionComparison.isBehind(upper, version, strategy))
                     .map(version -> new ReleaseLinkDto(version, releaseNotesUrl(component, version)))
                     .toList();
  }

  @Nullable
  private static String releaseNotesUrl(@Nonnull final ComponentEntity component, @Nonnull final String version) {
    if (nonBlank(component.getReleaseNotesUrl())) {
      return component.getReleaseNotesUrl().trim().replace("{version}", version);
    }
    return SourceType.GITHUB_RELEASE.equals(component.getSourceType()) && nonBlank(component.getSourceRef())
           ? "https://github.com/%s/releases/tag/%s".formatted(component.getSourceRef().trim(), version)
           : null;
  }

  /**
   * Whether a stop lies between here and there — and, where that cannot be
   * decided, shown anyway.
   *
   * <p>A version nothing can order answers "behind" in both directions, so a
   * strict reading would drop exactly the stops that are written in an unusual
   * form. Showing one stop too many costs a glance; hiding one means somebody
   * jumps over a version the release notes said cannot be skipped.
   */
  private boolean between(final String version, @Nonnull final String current, @Nonnull final String upper,
                          @Nonnull final ComponentEntity component) {
    if (!nonBlank(version)) {
      return false;
    }
    final boolean above = VersionComparison.isBehind(current, version, component.getVersionStrategy());
    final boolean below = !VersionComparison.isBehind(upper, version, component.getVersionStrategy());
    if (above && below) {
      return true;
    }
    // Unorderable in both directions: neither above nor below can be trusted.
    return above && VersionComparison.isBehind(version, current, component.getVersionStrategy());
  }

}
