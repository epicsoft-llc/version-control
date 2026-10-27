package one.epicsoft.vc.jpa.repository;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
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
public interface ComponentRepository extends JpaRepository<ComponentEntity, UUID> {

  Optional<ComponentEntity> findByIdAndDeletedIsNull(@Nonnull UUID id);

  List<ComponentEntity> findAllByProjectIdAndDeletedIsNullOrderByNameAsc(@Nonnull UUID projectId);

  boolean existsByProjectIdAndNameAndDeletedIsNull(@Nonnull UUID projectId, @Nonnull String name);

  /**
   * The components the scheduler may check now, the longest overdue first.
   *
   * <p>A component that was never checked has no due date and goes first: it is
   * the one nothing is known about. MANUAL is excluded because there is nothing
   * to ask, and a disabled check means somebody decided not to ask.
   */
  @Query("""
      SELECT c FROM ComponentEntity c
      WHERE c.deleted IS NULL
        AND c.checkEnabled = TRUE
        AND c.sourceType <> one.epicsoft.vc.enums.SourceType.MANUAL
        AND (c.nextCheckDue IS NULL OR c.nextCheckDue <= :now)
      ORDER BY c.nextCheckDue ASC NULLS FIRST
      """)
  List<ComponentEntity> findDue(@Nonnull @Param("now") Instant now, @Nonnull Pageable pageable);

  /**
   * Components that name an end-of-life product but were never looked up.
   *
   * <p>Separate from the due query above: the first lookup has to happen before
   * a status row exists, and joining that case into the due query would mean no
   * component is ever asked about a first time.
   */
  @Query("""
      SELECT c FROM ComponentEntity c
      WHERE c.deleted IS NULL
        AND c.eolProduct IS NOT NULL
        AND TRIM(c.eolProduct) <> ''
        AND NOT EXISTS (SELECT 1 FROM EolStatusEntity es WHERE es.componentId = c.id)
      ORDER BY c.name ASC
      """)
  List<ComponentEntity> findWithoutEolStatus(@Nonnull Pageable pageable);

  /** The first lookup starts here: until then a component has no check row that could be due. */
  @Query("""
      SELECT c FROM ComponentEntity c
      WHERE c.deleted IS NULL
        AND c.cpeVendor IS NOT NULL
        AND c.cpeProduct IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM VulnerabilityCheckEntity vc WHERE vc.componentId = c.id)
      ORDER BY c.name ASC
      """)
  List<ComponentEntity> findWithoutVulnerabilityCheck(@Nonnull Pageable pageable);
}
