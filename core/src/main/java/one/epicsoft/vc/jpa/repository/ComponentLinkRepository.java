package one.epicsoft.vc.jpa.repository;

import jakarta.annotation.Nonnull;
import one.epicsoft.vc.enums.LinkOrigin;
import one.epicsoft.vc.jpa.entity.ComponentLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ComponentLinkRepository extends JpaRepository<ComponentLinkEntity, UUID> {

  List<ComponentLinkEntity> findAllByComponentIdInOrderBySortOrderAsc(@Nonnull Collection<UUID> componentIds);

  List<ComponentLinkEntity> findAllByComponentIdAndOriginOrderBySortOrderAsc(@Nonnull UUID componentId, @Nonnull LinkOrigin origin);

  @Modifying
  @Query("DELETE FROM ComponentLinkEntity cl WHERE cl.componentId = :componentId AND cl.origin IN :origins")
  void deleteByComponentAndOrigins(@Nonnull @Param("componentId") UUID componentId,
                                   @Nonnull @Param("origins") Collection<LinkOrigin> origins);
}
