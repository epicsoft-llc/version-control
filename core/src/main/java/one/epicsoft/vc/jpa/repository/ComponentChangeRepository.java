package one.epicsoft.vc.jpa.repository;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.jpa.entity.ComponentChangeEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ComponentChangeRepository extends JpaRepository<ComponentChangeEntity, UUID> {

  List<ComponentChangeEntity> findAllByComponentIdOrderByChangedAtDesc(@Nonnull UUID componentId);

  @Query("""
      SELECT cc FROM ComponentChangeEntity cc
      WHERE cc.component.projectId = :projectId
        AND cc.component.deleted IS NULL
      ORDER BY cc.changedAt DESC
      """)
  List<ComponentChangeEntity> findRecentByProject(@Nonnull @Param("projectId") UUID projectId, @Nonnull Pageable page);
}
