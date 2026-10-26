package one.epicsoft.vc.jpa.repository;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.jpa.entity.EolStatusEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EolStatusRepository extends JpaRepository<EolStatusEntity, UUID> {

  Optional<EolStatusEntity> findByComponentId(@Nonnull UUID componentId);

  void deleteByComponentId(@Nonnull UUID componentId);

  @Query("""
      SELECT es FROM EolStatusEntity es
      WHERE es.component.projectId = :projectId
        AND es.component.deleted IS NULL
      """)
  List<EolStatusEntity> findAllByProject(@Nonnull @Param("projectId") UUID projectId);

  /**
   * The components whose end-of-life date may be asked about again.
   *
   * <p>A component that carries an eol product but has no status row yet is not
   * in here — those are found through the component itself, so that the first
   * lookup happens without a row having to exist first.
   */
  @Query("""
      SELECT es FROM EolStatusEntity es
      WHERE es.component.deleted IS NULL
        AND es.component.eolProduct IS NOT NULL
        AND (es.nextCheckDue IS NULL OR es.nextCheckDue <= :now)
      ORDER BY es.nextCheckDue ASC NULLS FIRST
      """)
  List<EolStatusEntity> findDue(@Nonnull @Param("now") Instant now, @Nonnull Pageable pageable);
}
