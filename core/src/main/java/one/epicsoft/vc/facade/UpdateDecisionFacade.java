package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.UpdateDecisionDto;
import one.epicsoft.vc.dto.UpdateDecisionRequest;
import one.epicsoft.vc.enums.DecisionState;
import one.epicsoft.vc.exception.AppValidationException;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.UpdateCheckEntity;
import one.epicsoft.vc.jpa.entity.UpdateDecisionEntity;
import one.epicsoft.vc.mapper.UpdateDecisionMapper;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.ProjectService;
import one.epicsoft.vc.service.UpdateCheckService;
import one.epicsoft.vc.service.UpdateDecisionService;
import one.epicsoft.vc.version.VersionComparison;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.isNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * What somebody decided about an available update.
 *
 * <p>One row per component, overwritten. The decision records the version it was
 * about, and the check reopens it when a newer one shows up — a postponement is
 * a statement about one version, never a standing order. A hold is the one
 * standing order, and it has to say why.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateDecisionFacade {

  private final UpdateDecisionService updateDecisionService;
  private final UpdateCheckService updateCheckService;
  private final ComponentService componentService;
  private final ProjectService projectService;
  private final UpdateDecisionMapper updateDecisionMapper;
  private final ComponentChangeFacade componentChangeFacade;

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<UpdateDecisionDto> findAllByComponent(@Nonnull final UUID componentId) {
    this.componentService.getById(componentId);
    return this.updateDecisionMapper.toDtos(this.updateDecisionService.findByComponent(componentId)
                                                .stream().toList());
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<UpdateDecisionDto> findAllByProject(@Nonnull final UUID projectId) {
    this.projectService.getById(projectId);
    return this.updateDecisionMapper.toDtos(this.updateDecisionService.findAllByProject(projectId));
  }

  /**
   * Records the decision, together with the version it is about.
   *
   * <p>A postponement without a date is refused: without one it is a note that
   * nobody comes back to, which is exactly the state this is meant to replace.
   */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public UpdateDecisionDto decide(@Nonnull final UpdateDecisionRequest request) {
    final ComponentEntity component = this.componentService.getById(request.getComponentId());
    if (DecisionState.BLOCKED.equals(request.getState())) {
      // Derived from the dependency check. Set by hand it would claim a blocker
      // that nothing points at, and nothing would ever clear it.
      throw AppValidationException.decisionNotSettable(request.getComponentId());
    }
    if (DecisionState.POSTPONED.equals(request.getState())
        && (isNull(request.getSnoozeUntil()) || request.getSnoozeUntil().isBefore(Instant.now()))) {
      // A date in the past is the same thing as no date: the entry is back on the
      // list before the reader has finished reading the dialog.
      throw AppValidationException.snoozeDateRequired(request.getComponentId());
    }
    if (DecisionState.HELD.equals(request.getState()) && !nonBlank(request.getNote())) {
      // A hold without a reason is the frozen component nobody dares to touch
      // three years later, because nobody remembers why.
      throw AppValidationException.holdReasonRequired(request.getComponentId());
    }

    final UpdateDecisionEntity decision = this.updateDecisionService.findByComponent(component.getId())
                                              .orElseGet(() -> newDecision(component));
    final DecisionState before = decision.getState();
    decision.setState(request.getState());
    decision.setSnoozeUntil(request.getSnoozeUntil());
    decision.setNote(request.getNote());
    decision.setReleaseUrl(request.getReleaseUrl());
    decision.setDecidedAt(Instant.now());
    // What is on the table right now, so that anything newer reopens this.
    decision.setDecidedForVersion(this.updateCheckService.findByComponent(component.getId())
                                      .map(UpdateCheckEntity::getLatestVersion)
                                      .orElse(null));

    final UpdateDecisionEntity saved = this.updateDecisionService.save(decision);
    this.componentChangeFacade.recordDecision(component, before, saved.getState(), saved.getNote());
    log.info("Recorded decision {} for component {}", saved.getState(), component.getId());
    return this.updateDecisionMapper.toDto(saved);
  }

  /**
   * Reopens a decision when the version it was about is no longer the newest.
   *
   * <p>Called by the check, in the same transaction that stores the new version.
   * The note stays: what somebody wrote about the last one is the most useful
   * thing there is when deciding about the next.
   *
   * @return true when a decision was reopened
   */
  @Transactional(rollbackFor = Throwable.class)
  public boolean reopenIfNewerVersion(@Nonnull final UUID componentId, @Nonnull final String latestVersion) {
    final Optional<UpdateDecisionEntity> stored = this.updateDecisionService.findByComponent(componentId);
    if (stored.isEmpty()) {
      return false;
    }
    final UpdateDecisionEntity decision = stored.get();
    if (DecisionState.OPEN.equals(decision.getState()) || decision.getState().isPermanent()) {
      return false;
    }
    // A decision taken while nothing was available covers nothing; the first
    // version to show up reopens it.
    if (nonBlank(decision.getDecidedForVersion())
        // Newer, not merely different. Tightening a rule can lower the version a
        // check reports — a component pinned back from 18.0 to 17.6 has nothing
        // new about it, and reopening there would ask a settled question again.
        && !VersionComparison.isBehind(decision.getDecidedForVersion(), latestVersion,
                                       decision.getComponent().getVersionStrategy())) {
      return false;
    }
    log.info("Reopening decision for component {}: {} supersedes {}", componentId, latestVersion,
             decision.getDecidedForVersion());
    this.componentChangeFacade.recordDecision(decision.getComponent(), decision.getState(), DecisionState.OPEN, null);
    decision.setState(DecisionState.OPEN);
    decision.setSnoozeUntil(null);
    decision.setDecidedForVersion(null);
    decision.setDecidedAt(Instant.now());
    this.updateDecisionService.save(decision);
    return true;
  }

  @Nonnull
  private static UpdateDecisionEntity newDecision(@Nonnull final ComponentEntity component) {
    final UpdateDecisionEntity decision = new UpdateDecisionEntity();
    decision.setComponent(component);
    decision.setDecidedAt(Instant.now());
    return decision;
  }
}
