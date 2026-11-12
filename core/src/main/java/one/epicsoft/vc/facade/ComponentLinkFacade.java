package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.config.LinkProperties;
import one.epicsoft.vc.data.FoundLink;
import one.epicsoft.vc.data.LinkSearchDraft;
import one.epicsoft.vc.data.LinkSearchOutcome;
import one.epicsoft.vc.dto.ComponentLinkDto;
import one.epicsoft.vc.dto.LinkSearchDto;
import one.epicsoft.vc.enums.CheckState;
import one.epicsoft.vc.enums.LinkOrigin;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentLinkEntity;
import one.epicsoft.vc.jpa.entity.LinkSearchEntity;
import one.epicsoft.vc.link.ProjectLinks;
import one.epicsoft.vc.mapper.ComponentLinkMapper;
import one.epicsoft.vc.service.ComponentLinkService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.LinkSearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentLinkFacade {

  public static final Set<LinkOrigin> SEARCHED = Set.of(LinkOrigin.END_OF_LIFE, LinkOrigin.DOCKER_HUB, LinkOrigin.GITHUB);

  private final ComponentService componentService;
  private final ComponentLinkService componentLinkService;
  private final LinkSearchService linkSearchService;
  private final ComponentLinkMapper componentLinkMapper;
  private final LinkProperties linkProperties;

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public LinkSearchDraft collect(@Nonnull final UUID componentId) {
    return draftOf(this.componentService.getById(componentId));
  }

  @Transactional(rollbackFor = Throwable.class)
  public void remember(@Nonnull final LinkSearchDraft draft, @Nonnull final LinkSearchOutcome outcome) {
    final ComponentEntity component = this.componentService.getById(draft.componentId());
    if (!draftOf(component).equals(draft)) {
      // Edited during the search: the answer belongs to the old source.
      log.info("Links of component {} changed source during the search; result dropped", component.getId());
      return;
    }
    outcome.found().forEach((origin, found) -> {
      final List<ComponentLinkEntity> links = new ArrayList<>();
      final Set<String> seen = new HashSet<>();
      for (final FoundLink link : found) {
        if (seen.add(ProjectLinks.keyOf(link.url()))) {
          links.add(entity(link, origin, links.size()));
        }
      }
      this.componentLinkService.replace(component, Set.of(origin), links);
    });

    final Instant now = Instant.now();
    final LinkSearchEntity search = this.linkSearchService.findByComponent(component.getId()).orElseGet(() -> newSearch(component));
    search.setCheckedAt(now);
    search.setNextCheckDue(now.plus(this.linkProperties.getCheckInterval()));
    search.setCheckState(outcome.failures().isEmpty() ? CheckState.OK : CheckState.ERROR);
    search.setErrorMessage(outcome.failures().isEmpty() ? null : String.join("; ", outcome.failures()));
    this.linkSearchService.save(search);
  }

  /** Its own transaction: the failed one rolls back the due date, and the component would head the queue for good. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
  public void postponeAfterUnexpectedFailure(@Nonnull final UUID componentId) {
    final ComponentEntity component = this.componentService.getById(componentId);
    final LinkSearchEntity search = this.linkSearchService.findByComponent(componentId).orElseGet(() -> newSearch(component));
    search.setCheckedAt(Instant.now());
    search.setNextCheckDue(Instant.now().plus(this.linkProperties.getCheckInterval()));
    search.setCheckState(CheckState.ERROR);
    search.setErrorMessage("The link search ended unexpectedly; see the log for the cause");
    this.linkSearchService.save(search);
  }

  /** Kept by hand first, then derived, then found, so the first of one address wins. */
  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public LinkSearchDto findByComponent(@Nonnull final UUID componentId) {
    final ComponentEntity component = this.componentService.getById(componentId);
    final List<ComponentLinkEntity> stored = this.componentLinkService.findAllByComponents(List.of(componentId));

    final List<ComponentLinkDto> candidates = new ArrayList<>();
    stored.stream().filter(link -> LinkOrigin.MANUAL.equals(link.getOrigin())).map(this.componentLinkMapper::toDto).forEach(candidates::add);
    ProjectLinks.fromFields(component.getSourceType(), component.getSourceRef(), component.getEolProduct(), component.getUpgradeDocUrl())
                .forEach(link -> candidates.add(dto(link, LinkOrigin.SOURCE)));
    stored.stream().filter(link -> SEARCHED.contains(link.getOrigin())).map(this.componentLinkMapper::toDto).forEach(candidates::add);

    final Set<String> seen = new HashSet<>();
    final List<ComponentLinkDto> links = candidates.stream()
                                                   .filter(link -> seen.add(ProjectLinks.keyOf(link.getUrl())))
                                                   .sorted(Comparator.comparing(ComponentLinkDto::getKind))
                                                   .toList();
    final LinkSearchDto dto = new LinkSearchDto();
    dto.setComponentId(componentId);
    dto.setLinks(links);
    final Optional<LinkSearchEntity> search = this.linkSearchService.findByComponent(componentId);
    search.ifPresent(found -> {
      dto.setCheckedAt(found.getCheckedAt());
      dto.setCheckState(found.getCheckState());
      dto.setErrorMessage(found.getErrorMessage());
    });
    return dto;
  }

  @Nonnull
  private static LinkSearchDraft draftOf(@Nonnull final ComponentEntity component) {
    return new LinkSearchDraft(component.getId(), component.getSourceType(), component.getSourceRef(), component.getEolProduct());
  }

  @Nonnull
  private static ComponentLinkEntity entity(@Nonnull final FoundLink link, @Nonnull final LinkOrigin origin, final int sortOrder) {
    final ComponentLinkEntity entity = new ComponentLinkEntity();
    entity.setUrl(link.url());
    entity.setKind(link.kind());
    entity.setOrigin(origin);
    entity.setSortOrder(sortOrder);
    return entity;
  }

  @Nonnull
  private static ComponentLinkDto dto(@Nonnull final FoundLink link, @Nonnull final LinkOrigin origin) {
    final ComponentLinkDto dto = new ComponentLinkDto();
    dto.setUrl(link.url());
    dto.setKind(link.kind());
    dto.setOrigin(origin);
    return dto;
  }

  @Nonnull
  private static LinkSearchEntity newSearch(@Nonnull final ComponentEntity component) {
    final LinkSearchEntity search = new LinkSearchEntity();
    search.setComponent(component);
    return search;
  }
}
