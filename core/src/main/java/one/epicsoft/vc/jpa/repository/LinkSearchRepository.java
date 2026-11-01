package one.epicsoft.vc.jpa.repository;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.jpa.entity.LinkSearchEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkSearchRepository extends JpaRepository<LinkSearchEntity, UUID> {

  Optional<LinkSearchEntity> findByComponentId(@Nonnull UUID componentId);

  void deleteByComponentId(@Nonnull UUID componentId);

  /** The ones never searched have no row and come from ComponentRepository. */
  @Query("""
      SELECT ls FROM LinkSearchEntity ls
      WHERE ls.component.deleted IS NULL
        AND (ls.nextCheckDue IS NULL OR ls.nextCheckDue <= :now)
      ORDER BY ls.nextCheckDue ASC NULLS FIRST
      """)
  List<LinkSearchEntity> findDue(@Nonnull @Param("now") Instant now, @Nonnull Pageable pageable);
}
