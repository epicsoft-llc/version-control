package one.epicsoft.vc.mapper;

import one.epicsoft.vc.dto.ComponentLinkDto;
import one.epicsoft.vc.jpa.entity.ComponentLinkEntity;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ComponentLinkMapper {

  ComponentLinkDto toDto(final ComponentLinkEntity entity);

  List<ComponentLinkDto> toDtos(final List<ComponentLinkEntity> entities);
}
