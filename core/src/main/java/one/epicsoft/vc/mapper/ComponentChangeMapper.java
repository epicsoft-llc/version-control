package one.epicsoft.vc.mapper;

import one.epicsoft.vc.dto.ComponentChangeDto;
import one.epicsoft.vc.jpa.entity.ComponentChangeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ComponentChangeMapper {

  @Mapping(target = "componentName", source = "component.name")
  ComponentChangeDto toDto(final ComponentChangeEntity entity);

  List<ComponentChangeDto> toDtos(final List<ComponentChangeEntity> entities);
}
