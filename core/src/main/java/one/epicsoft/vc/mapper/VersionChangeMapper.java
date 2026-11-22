package one.epicsoft.vc.mapper;

import one.epicsoft.vc.dto.VersionChangeCorrectionRequest;
import one.epicsoft.vc.dto.VersionChangeDto;
import one.epicsoft.vc.jpa.entity.VersionChangeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface VersionChangeMapper {

  /**
   * Component and environment come along so a history list can name both. An entry is updated only by a
   * correction, so its update time is when it was last corrected.
   */
  @Mapping(target = "componentId", source = "componentEnvironment.componentId")
  @Mapping(target = "environmentId", source = "componentEnvironment.environmentId")
  @Mapping(target = "correctedAt", source = "updated")
  VersionChangeDto toDto(final VersionChangeEntity entity);

  List<VersionChangeDto> toDtos(final List<VersionChangeEntity> entities);

  /** The stored description as a correction would send it — what a merge patch is applied to. */
  VersionChangeCorrectionRequest toCorrectionRequest(final VersionChangeEntity entity);
}
