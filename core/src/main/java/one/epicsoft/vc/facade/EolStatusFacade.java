package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.adapter.EndOfLifeAdapter;
import one.epicsoft.vc.adapter.SourceException;
import one.epicsoft.vc.config.EolProperties;
import one.epicsoft.vc.dto.EolStatusDto;
import one.epicsoft.vc.enums.CheckState;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentEntity;
import one.epicsoft.vc.jpa.entity.EolStatusEntity;
import one.epicsoft.vc.mapper.EolStatusMapper;
import one.epicsoft.vc.service.ComponentEnvironmentService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.EolStatusService;
import one.epicsoft.vc.version.TagRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.isNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * When support for what is running ends.
 *
 * <p>Its own cycle, its own error state, its own thresholds — an end of support
 * is not a bigger patch. It has a date, and no decision postpones it, which is
 * why nothing here reads or writes {@code UpdateDecision}.
 *
 * <p>A component without an end-of-life product is not checked and is not an
 * error. Naming a product is how somebody says "watch this"; leaving it empty is
 * how they say the question does not apply.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EolStatusFacade {

  private final ComponentService componentService;
  private final ComponentEnvironmentService componentEnvironmentService;
  private final EolStatusService eolStatusService;
  private final EndOfLifeAdapter endOfLifeAdapter;
  private final EolProperties eolProperties;
  private final EolStatusMapper eolStatusMapper;

  /**
   * Looks one component up now.
   *
   * @return empty when the component names no product — a regular state, not a fault
   */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public Optional<EolStatusEntity> check(@Nonnull final UUID componentId) {
    final ComponentEntity component = this.componentService.getById(componentId);
    if (!nonBlank(component.getEolProduct())) {
      return Optional.empty();
    }

    final Instant now = Instant.now();
    final EolStatusEntity status = this.eolStatusService.findByComponent(componentId).orElseGet(() -> newStatus(component));
    status.setCheckedAt(now);
    status.setNextCheckDue(now.plus(this.eolProperties.getCheckInterval()));
    try {
      final EndOfLifeAdapter.EndOfLife found = this.endOfLifeAdapter.fetch(component, this.cycleOf(component));
      status.setEolDate(found.eolDate());
      status.setSupportedUntil(found.supportedUntil());
      status.setEol(found.eol());
      status.setCheckState(CheckState.OK);
      status.setErrorMessage(null);
    } catch (final SourceException exception) {
      log.warn("End-of-life lookup for component {} failed: {}", componentId, exception.getMessage());
      // The dates of the last good answer stay: a deadline that was known
      // yesterday does not become unknown because a request failed today.
      status.setCheckState(CheckState.ERROR);
      status.setErrorMessage(exception.getMessage());
    }
    return Optional.of(this.eolStatusService.save(status));
  }

  /**
   * Moves a status out of the way after a failure nobody planned for, in its own
   * transaction — the one that failed is rolling back, and the due date with it.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
  public void postponeAfterUnexpectedFailure(@Nonnull final UUID componentId) {
    final ComponentEntity component = this.componentService.getById(componentId);
    // A row has to exist afterwards, even where none did. The component this
    // happens to most often is one that was never looked up, and updating a row
    // that is not there would leave it first in the queue on every tick — for
    // good, and blocking every other component behind it.
    final EolStatusEntity status = this.eolStatusService.findByComponent(componentId)
                                       .orElseGet(() -> newStatus(component));
    status.setCheckedAt(Instant.now());
    status.setNextCheckDue(Instant.now().plus(this.eolProperties.getCheckInterval()));
    status.setCheckState(CheckState.ERROR);
    status.setErrorMessage("The end-of-life lookup ended unexpectedly; see the log for the cause");
    this.eolStatusService.save(status);
  }

  /**
   * Which release line to ask about: the one configured, or the leading segments
   * of what production runs. A component on 17.2 is asking about cycle 17.
   */
  @Nullable
  private String cycleOf(@Nonnull final ComponentEntity component) {
    if (nonBlank(component.getEolCycle())) {
      return component.getEolCycle();
    }
    return this.componentEnvironmentService.findAllByComponent(component.getId()).stream()
               .filter(ComponentEnvironmentEntity::isActive)
               .filter(cell -> nonBlank(cell.getCurrentVersion()))
               .sorted(Comparator.comparing((ComponentEnvironmentEntity cell) -> cell.getEnvironment().isProduction())
                                 .reversed())
               .map(ComponentEnvironmentEntity::getCurrentVersion)
               .map(TagRule::majorOf)
               .filter(java.util.Objects::nonNull)
               .findFirst()
               .orElse(null);
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<EolStatusEntity> findAllByProject(@Nonnull final UUID projectId) {
    return this.eolStatusService.findAllByProject(projectId);
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<EolStatusDto> findAllByProjectAsDto(@Nonnull final UUID projectId) {
    return this.eolStatusMapper.toDtos(this.eolStatusService.findAllByProject(projectId));
  }

  /** Never looked up, or nothing to look up: an empty list, not an error. */
  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<EolStatusDto> findAllByComponent(@Nonnull final UUID componentId) {
    this.componentService.getById(componentId);
    return this.eolStatusMapper.toDtos(this.eolStatusService.findByComponent(componentId).stream().toList());
  }

  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public List<EolStatusDto> checkAsDto(@Nonnull final UUID componentId) {
    return this.eolStatusMapper.toDtos(this.check(componentId).stream().toList());
  }

  @Nonnull
  private static EolStatusEntity newStatus(@Nonnull final ComponentEntity component) {
    final EolStatusEntity status = new EolStatusEntity();
    status.setComponent(component);
    status.setEol(Boolean.FALSE);
    if (isNull(status.getCheckState())) {
      status.setCheckState(CheckState.OK);
    }
    return status;
  }
}
