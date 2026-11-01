package one.epicsoft.vc.service;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import one.epicsoft.vc.jpa.entity.LinkSearchEntity;
import one.epicsoft.vc.jpa.repository.LinkSearchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkSearchService {

  private final LinkSearchRepository linkSearchRepository;

  @Nonnull
  public Optional<LinkSearchEntity> findByComponent(@Nonnull final UUID componentId) {
    return this.linkSearchRepository.findByComponentId(componentId);
  }

  @Nonnull
  public List<LinkSearchEntity> findDue(@Nonnull final Instant now, final int limit) {
    return this.linkSearchRepository.findDue(now, PageRequest.of(0, limit));
  }

  @Nonnull
  public LinkSearchEntity save(@Nonnull final LinkSearchEntity entity) {
    return this.linkSearchRepository.save(entity);
  }

  public void deleteByComponent(@Nonnull final UUID componentId) {
    this.linkSearchRepository.deleteByComponentId(componentId);
  }
}
