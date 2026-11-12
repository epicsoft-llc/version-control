package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.adapter.SourceAdapter;
import one.epicsoft.vc.adapter.SourceException;
import one.epicsoft.vc.config.CheckProperties;
import one.epicsoft.vc.enums.CheckState;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.dto.UpdateCheckDto;
import one.epicsoft.vc.dto.VersionPreviewDto;
import one.epicsoft.vc.dto.VersionPreviewRequest;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentEnvironmentEntity;
import one.epicsoft.vc.jpa.entity.ComponentPackageEntity;
import one.epicsoft.vc.jpa.entity.UpdateCheckEntity;
import one.epicsoft.vc.jpa.entity.UpdateCheckPackageEntity;
import one.epicsoft.vc.service.ComponentEnvironmentService;
import one.epicsoft.vc.mapper.UpdateCheckMapper;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.ProjectService;
import one.epicsoft.vc.service.UpdateCheckService;
import one.epicsoft.vc.version.TagRule;
import one.epicsoft.vc.version.VersionCandidate;
import one.epicsoft.vc.version.VersionRuleException;
import one.epicsoft.vc.version.VersionSelector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * Asks a source what exists and writes down what it found.
 *
 * <p>The one rule that governs everything here: a check that could not answer is
 * {@link CheckState#ERROR}, never a quiet "nothing new". A rule that matches
 * nothing, an address that 404s, a payload that changed shape — all of them end
 * as an error carrying the reason. The opposite behaviour is the only failure in
 * this application that stays invisible for months, because a component that is
 * never reported as behind looks exactly like one that is up to date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateCheckFacade {

  private static final String CANDIDATE_SEPARATOR = "\n";

  private static final String FIELD_SEPARATOR = "\t";

  /** Enough to show a rule preview; the full list of an old repository is noise. */
  private static final int STORED_CANDIDATES = 200;

  private final ComponentService componentService;
  private final ComponentEnvironmentService componentEnvironmentService;
  private final UpdateCheckService updateCheckService;
  private final VersionSelector versionSelector;
  private final CheckProperties checkProperties;
  private final ProjectService projectService;
  private final UpdateCheckMapper updateCheckMapper;
  private final UpdateDecisionFacade updateDecisionFacade;
  private final List<SourceAdapter> adapters;

  /** Checks one component now. */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public UpdateCheckEntity check(@Nonnull final UUID componentId) {
    final ComponentEntity component = this.componentService.getById(componentId);
    final UpdateCheckEntity check = this.updateCheckService.findByComponent(componentId)
                                                           .orElseGet(() -> newCheck(component));

    final Instant now = Instant.now();
    check.setCheckedAt(now);
    if (SourceType.MANUAL.equals(component.getSourceType())) {
      // A regular case, not a fault: nobody publishes this component anywhere,
      // its version is whatever somebody records. Marking it as an error would
      // put a permanent complaint on a component that is set up correctly.
      return this.storeNotApplicable(component, check, now);
    }

    try {
      final List<VersionCandidate> candidates = this.adapterFor(component).fetchCandidates(component);
      return this.storeSuccess(component, check, candidates, now);
    } catch (final SourceException | VersionRuleException exception) {
      log.warn("Check of component {} failed: {}", componentId, exception.getMessage());
      return this.storeFailure(component, check, exception.getMessage(), now);
    }
  }

  /**
   * Which tags the rule accepts and which of them would win.
   *
   * <p>The candidates of the last check are reused while they are younger than
   * the minimum interval, and only outside it does a preview cost the source a
   * request — by running a real check, which stores what it found. Fifty clicks
   * on the preview button are one request, not fifty.
   */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public List<VersionCandidate> preview(@Nonnull final VersionPreviewRequest request) {
    final ComponentEntity component = this.componentService.getById(request.getComponentId());
    if (SourceType.MANUAL.equals(component.getSourceType())) {
      // Not a rule problem: there is nothing to ask. Pointing the reader at their
      // pattern here would send them looking in the wrong place.
      throw new VersionRuleException("This component has no source to query; its version is only ever recorded by hand");
    }
    final UUID componentId = component.getId();

    final Optional<UpdateCheckEntity> stored = this.updateCheckService.findByComponent(componentId);
    final List<VersionCandidate> candidates =
        stored.filter(this::withinBudget)
              .map(UpdateCheckFacade::storedCandidates)
              // Outside the bound the preview pays for itself by running a real
              // check, which stores what it found for every preview after it.
              .orElseGet(() -> storedCandidates(this.check(componentId)));
    if (candidates.isEmpty()) {
      throw new VersionRuleException("The last check of this component found no versions");
    }
    // Every part of the rule comes from the form where the form has one, and from
    // the stored component where it does not.
    return this.versionSelector.matching(candidates,
                                         nonBlank(request.getTagPattern())
                                             ? request.getTagPattern()
                                             : this.rulePattern(component, candidates),
                                         nonBlank(request.getMajorConstraint())
                                             ? request.getMajorConstraint()
                                             : component.getMajorConstraint(),
                                         isNull(request.getAllowPrerelease())
                                             ? component.isAllowPrerelease()
                                             : request.getAllowPrerelease(),
                                         isNull(request.getVersionStrategy())
                                             ? component.getVersionStrategy()
                                             : request.getVersionStrategy());
  }

  // #### For the API
  /**
   * Never checked is an empty list, not a 404: a component that nobody has asked
   * about yet is a normal state, and an error would send the interface looking
   * for a fault that is not there.
   */
  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<UpdateCheckDto> findAllByComponent(@Nonnull final UUID componentId) {
    this.componentService.getById(componentId);
    return this.toDtos(this.updateCheckService.findByComponent(componentId).stream().toList());
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<UpdateCheckDto> findAllByProject(@Nonnull final UUID projectId) {
    this.projectService.getById(projectId);
    return this.toDtos(this.updateCheckService.findAllByProject(projectId));
  }

  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public UpdateCheckDto checkNow(@Nonnull final UUID componentId) {
    return this.toDtos(List.of(this.check(componentId))).getFirst();
  }

  /**
   * Reads the last check again under the rule as it stands now — from the stored
   * candidates, without asking the source. Otherwise a tightened rule keeps
   * reporting what the old one found until the next check: a component held on 17
   * would still be offered 18.
   */
  @Transactional(rollbackFor = Throwable.class)
  public void reapplyRule(@Nonnull final UUID componentId) {
    final ComponentEntity component = this.componentService.getById(componentId);
    this.updateCheckService.findByComponent(componentId)
                           .filter(check -> CheckState.OK.equals(check.getCheckState()))
                           .ifPresent(check -> this.reapply(component, check, storedCandidates(check)));
  }

  /**
   * The preview as the screen wants it: a rule that matches nothing, or does not
   * compile at all, is the normal state while somebody is writing one. It comes
   * back as an empty list with the reason, not as a server error.
   */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public VersionPreviewDto previewRule(@Nonnull final VersionPreviewRequest request) {
    final VersionPreviewDto preview = new VersionPreviewDto();
    try {
      final List<VersionCandidate> matching = this.preview(request);
      preview.setMatching(this.updateCheckMapper.toCandidates(matching));
      preview.setWinner(matching.isEmpty() ? null : matching.getFirst().tag());
    } catch (final VersionRuleException | SourceException exception) {
      preview.setMatching(List.of());
      preview.setMessage(exception.getMessage());
    }
    return preview;
  }

  /** Loads the packages of every check in one query rather than one per row. */
  @Nonnull
  private List<UpdateCheckDto> toDtos(@Nonnull final List<UpdateCheckEntity> entities) {
    final List<UUID> ids = entities.stream().map(UpdateCheckEntity::getId).toList();
    final Map<UUID, List<UpdateCheckPackageEntity>> packages =
        this.updateCheckService.findPackages(ids).stream()
            .collect(Collectors.groupingBy(UpdateCheckPackageEntity::getUpdateCheckId));

    return entities.stream().map(entity -> {
      final UpdateCheckDto dto = this.updateCheckMapper.toDto(entity);
      dto.setPackages(this.updateCheckMapper.toPackageDtos(packages.getOrDefault(entity.getId(), List.of())));
      return dto;
    }).toList();
  }

  // #### Storing a result
  @Nonnull
  private UpdateCheckEntity storeSuccess(@Nonnull final ComponentEntity component,
                                         @Nonnull final UpdateCheckEntity check,
                                         @Nonnull final List<VersionCandidate> candidates,
                                         @Nonnull final Instant now) {
    final List<UpdateCheckPackageEntity> packageResults = this.applyResults(component, check, candidates);

    check.setCheckState(CheckState.OK);
    check.setErrorMessage(null);
    check.setLastSuccessfulCheckAt(now);
    check.setCandidateTags(candidates.stream()
                                     .limit(STORED_CANDIDATES)
                                     .map(UpdateCheckFacade::serialize)
                                     .collect(Collectors.joining(CANDIDATE_SEPARATOR)));

    component.setCheckFailureCount(0);
    component.setNextCheckDue(this.nextDue(this.checkProperties.getDefaultInterval(), now, true));
    this.componentService.save(component);

    // A decision covers the version it was made about. Reopening it here, in the
    // transaction that stores the newer one, is what keeps a postponement of
    // 17.1 from quietly swallowing 17.5 as well.
    if (nonBlank(check.getLatestVersion())) {
      this.updateDecisionFacade.reopenIfNewerVersion(component.getId(), check.getLatestVersion());
    }
    return this.saveWithPackages(check, packageResults);
  }

  private void reapply(@Nonnull final ComponentEntity component,
                       @Nonnull final UpdateCheckEntity check,
                       @Nonnull final List<VersionCandidate> candidates) {
    if (!candidates.isEmpty()) {
      try {
        final UpdateCheckEntity saved = this.saveWithPackages(check, this.applyResults(component, check, candidates));
        // A loosened rule can surface a newer version, and that reopens a decision just as a check would.
        if (nonBlank(saved.getLatestVersion())) {
          this.updateDecisionFacade.reopenIfNewerVersion(component.getId(), saved.getLatestVersion());
        }
      } catch (final VersionRuleException exception) {
        // A rule that matches nothing stored is left to the next check, which reports it as a failure.
        log.info("Rule of component {} matches none of the stored candidates: {}", component.getId(),
                 exception.getMessage());
      }
    }
  }

  /** The newest candidate per the rule; package results come back to be written after the check itself. */
  @Nonnull
  private List<UpdateCheckPackageEntity> applyResults(@Nonnull final ComponentEntity component,
                                                      @Nonnull final UpdateCheckEntity check,
                                                      @Nonnull final List<VersionCandidate> candidates) {
    final List<ComponentPackageEntity> packages = this.componentService.findPackages(component.getId());
    List<UpdateCheckPackageEntity> results = List.of();
    if (packages.isEmpty()) {
      this.applySingleResult(component, check, candidates);
    } else {
      results = this.applyPackageResults(component, check, candidates, packages);
    }
    return results;
  }

  @Nonnull
  private UpdateCheckEntity saveWithPackages(@Nonnull final UpdateCheckEntity check,
                                             @Nonnull final List<UpdateCheckPackageEntity> packageResults) {
    final UpdateCheckEntity saved = this.updateCheckService.save(check);
    if (!packageResults.isEmpty()) {
      // Only after the state is set: on a component's first check the flush in here
      // inserts its row, and the database refuses a check without a state.
      this.updateCheckService.replacePackages(saved, packageResults);
    }
    return saved;
  }

  private void applySingleResult(@Nonnull final ComponentEntity component,
                                 @Nonnull final UpdateCheckEntity check,
                                 @Nonnull final List<VersionCandidate> candidates) {
    final VersionCandidate latest = this.versionSelector.latest(candidates,
                                                                this.rulePattern(component, candidates),
                                                                component.getMajorConstraint(),
                                                                component.isAllowPrerelease(),
                                                                component.getVersionStrategy());
    check.setLatestVersion(latest.tag());
    check.setLatestReleasedAt(latest.releasedAt());
    check.setLatestDigest(latest.digest());
  }

  /**
   * One request answered for every package, so every package is evaluated from
   * the same list. The leading package is what the matrix compares against.
   *
   * <p>The rule here is the configured one, and nothing is derived from a
   * deployed tag: a Debian version carries an epoch, a revision and a
   * distribution suffix, so a pattern derived from it would describe the platform
   * rather than the version line. Ordering is the Debian comparison, and a
   * component that has to stay on one line uses the major constraint.
   */
  @Nonnull
  private List<UpdateCheckPackageEntity> applyPackageResults(@Nonnull final ComponentEntity component,
                                                             @Nonnull final UpdateCheckEntity check,
                                                             @Nonnull final List<VersionCandidate> candidates,
                                                             @Nonnull final List<ComponentPackageEntity> packages) {
    final Map<String, List<VersionCandidate>> byPackage =
        candidates.stream()
                  .filter(candidate -> nonBlank(candidate.packageName()))
                  .collect(Collectors.groupingBy(VersionCandidate::packageName));

    final List<UpdateCheckPackageEntity> results = new ArrayList<>();
    String leadingVersion = null;
    for (final ComponentPackageEntity configured : packages) {
      final List<VersionCandidate> forPackage = byPackage.get(configured.getName());
      if (isNull(forPackage) || forPackage.isEmpty()) {
        // Naming the package beats a generic "nothing found": the package list and
        // the repository disagree, and only the name says which entry to look at.
        throw new VersionRuleException("Package " + configured.getName() + " is not in the repository index");
      }
      final VersionCandidate latest = this.versionSelector.latest(forPackage,
                                                                  component.getTagPattern(),
                                                                  component.getMajorConstraint(),
                                                                  component.isAllowPrerelease(),
                                                                  component.getVersionStrategy());
      final UpdateCheckPackageEntity result = new UpdateCheckPackageEntity();
      result.setPackageName(configured.getName());
      result.setLatestVersion(latest.tag());
      results.add(result);
      if (configured.isLeadingPackage()) {
        leadingVersion = latest.tag();
      }
    }

    // Set only once every package resolved. Applying them one by one would
    // leave the leading version of a half finished run standing next to an error
    // state - a fresh number that no successful check ever produced.
    check.setLatestVersion(leadingVersion);
    return results;
  }

  @Nonnull
  private UpdateCheckEntity storeFailure(@Nonnull final ComponentEntity component,
                                         @Nonnull final UpdateCheckEntity check,
                                         @Nullable final String message,
                                         @Nonnull final Instant now) {
    check.setCheckState(CheckState.ERROR);
    check.setErrorMessage(message);
    // Latest version and last successful check stay as they were: how old the
    // last good answer is belongs on the screen next to the failure.

    final int failures = requireNonNullElse(component.getCheckFailureCount(), 0) + 1;
    component.setCheckFailureCount(failures);
    component.setNextCheckDue(this.nextDue(this.backoff(failures), now, false));
    this.componentService.save(component);
    return this.updateCheckService.save(check);
  }

  @Nonnull
  private UpdateCheckEntity storeNotApplicable(@Nonnull final ComponentEntity component,
                                               @Nonnull final UpdateCheckEntity check,
                                               @Nonnull final Instant now) {
    check.setCheckState(CheckState.NOT_APPLICABLE);
    check.setErrorMessage(null);
    component.setCheckFailureCount(0);
    component.setNextCheckDue(this.nextDue(this.checkProperties.getDefaultInterval(), now, true));
    this.componentService.save(component);
    return this.updateCheckService.save(check);
  }

  /**
   * Moves a component out of the way after a failure nobody planned for.
   *
   * <p>Its own transaction on purpose: the one that failed is rolling back, and
   * the new due date would roll back with it. The scheduler would then pick the
   * same component again on the next tick, and on every tick after that, and no
   * other component would ever be checked again.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
  public void postponeAfterUnexpectedFailure(@Nonnull final UUID componentId) {
    final ComponentEntity component = this.componentService.getById(componentId);
    final int failures = requireNonNullElse(component.getCheckFailureCount(), 0) + 1;
    component.setCheckFailureCount(failures);
    component.setNextCheckDue(this.nextDue(this.backoff(failures), Instant.now(), false));
    this.componentService.save(component);
  }

  // #### Rule
  /**
   * The rule of the component, or one derived from what is actually deployed.
   *
   * <p>Derived from the production environment where there is one, because that
   * is the tag the component is really running, and a rule derived from a test
   * environment would follow whatever was tried there last.
   */
  @Nullable
  private String rulePattern(@Nonnull final ComponentEntity component,
                             @Nonnull final List<VersionCandidate> candidates) {
    if (nonBlank(component.getTagPattern())) {
      return component.getTagPattern();
    }
    final String deployed = this.deployedVersion(component.getId());
    final String derived = TagRule.derivePattern(deployed);
    if (nonNull(derived)) {
      return derived;
    }
    if (!nonBlank(deployed)) {
      throw new VersionRuleException(
          "No tag pattern is configured and no deployed version is recorded, so no rule can be derived");
    }
    // A tag such as latest or stable has no version core. It moves, so the only
    // honest way to watch it is the digest, and the reader has to say so.
    throw new VersionRuleException("Deployed tag '" + deployed + "' carries no version to derive a rule from; "
                                   + "configure a tag pattern or use the digest strategy");
  }

  @Nullable
  private String deployedVersion(@Nonnull final UUID componentId) {
    return this.componentEnvironmentService.findAllByComponent(componentId).stream()
               .filter(ComponentEnvironmentEntity::isActive)
               .filter(cell -> nonBlank(cell.getCurrentVersion()))
               .sorted(Comparator.comparing((ComponentEnvironmentEntity cell) -> cell.getEnvironment().isProduction())
                                 .reversed())
               .map(ComponentEnvironmentEntity::getCurrentVersion)
               .findFirst()
               .orElse(null);
  }

  // #### Scheduling
  /**
   * True while the last request to the source is young enough that the preview
   * has to make do with what it brought back.
   *
   * <p>Any outcome counts, a failure included: that request went out too, and a
   * preview button that ignores it is a way around the bound. What the reader
   * gets in that case is the message that nothing was found, not another request.
   */
  private boolean withinBudget(@Nonnull final UpdateCheckEntity check) {
    return nonNull(check.getCheckedAt())
           && check.getCheckedAt().isAfter(Instant.now().minus(this.checkProperties.getMinInterval()));
  }

  /**
   * The next due date, spread by a random offset inside the configured jitter.
   * Without it the check times of every component drift onto the same minute and
   * stay there for good.
   *
   * @param applyFloor true after an answer, so the minimum interval holds; false
   *                   after a failure, where the back-off decides and is shorter
   *                   on purpose
   */
  @Nonnull
  private Instant nextDue(@Nonnull final Duration interval, @Nonnull final Instant now, final boolean applyFloor) {
    final long jitterSeconds = this.checkProperties.getJitter().toSeconds();
    final long offset = jitterSeconds <= 0 ? 0 : ThreadLocalRandom.current().nextLong(-jitterSeconds, jitterSeconds);
    final Instant due = now.plus(interval).plusSeconds(offset);
    if (!applyFloor) {
      return due;
    }
    // The floor wins over the jitter: it may push a check later, never earlier.
    final Instant floor = now.plus(this.checkProperties.getMinInterval());
    return due.isBefore(floor) ? floor : due;
  }

  @Nonnull
  private Duration backoff(final int failures) {
    final List<Duration> backoff = this.checkProperties.getBackoff();
    if (backoff.isEmpty()) {
      return this.checkProperties.getDefaultInterval();
    }
    return backoff.get(Math.min(failures, backoff.size()) - 1);
  }

  @Nonnull
  private SourceAdapter adapterFor(@Nonnull final ComponentEntity component) {
    return this.adapters.stream()
               .filter(adapter -> adapter.sourceType().equals(component.getSourceType()))
               .findFirst()
               // GHCR and a generic registry are deliberately not built yet; saying
               // so beats a check that stays silent about a component nobody watches.
               .orElseThrow(() -> new SourceException("No adapter is built for source type "
                                                      + component.getSourceType()));
  }

  @Nonnull
  private static UpdateCheckEntity newCheck(@Nonnull final ComponentEntity component) {
    final UpdateCheckEntity check = new UpdateCheckEntity();
    check.setComponent(component);
    return check;
  }

  /**
   * The cached candidates carry the date and the digest as well, not only the
   * tag: a preview that dropped them could not order by date, and the strategy
   * would fail on cached data while working on fresh data.
   */
  @Nonnull
  private static String serialize(@Nonnull final VersionCandidate candidate) {
    return String.join(FIELD_SEPARATOR,
                       candidate.tag(),
                       isNull(candidate.releasedAt()) ? "" : candidate.releasedAt().toString(),
                       isNull(candidate.digest()) ? "" : candidate.digest(),
                       isNull(candidate.packageName()) ? "" : candidate.packageName());
  }

  @Nonnull
  private static List<VersionCandidate> storedCandidates(@Nonnull final UpdateCheckEntity check) {
    if (!nonBlank(check.getCandidateTags())) {
      return List.of();
    }
    final List<VersionCandidate> candidates = new ArrayList<>();
    for (final String line : check.getCandidateTags().split(CANDIDATE_SEPARATOR)) {
      if (line.isBlank()) {
        continue;
      }
      final String[] fields = line.split(FIELD_SEPARATOR, -1);
      candidates.add(new VersionCandidate(fields[0],
                                          fields.length > 1 && nonBlank(fields[1]) ? Instant.parse(fields[1]) : null,
                                          fields.length > 2 && nonBlank(fields[2]) ? fields[2] : null,
                                          fields.length > 3 && nonBlank(fields[3]) ? fields[3] : null));
    }
    return candidates;
  }
}
