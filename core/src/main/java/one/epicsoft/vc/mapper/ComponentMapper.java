package one.epicsoft.vc.mapper;

import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentPackageDto;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentPackageEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ComponentMapper {

  @Mapping(target = "packages", ignore = true)
  @Mapping(target = "upgradeStops", ignore = true)
  @Mapping(target = "dependencies", ignore = true)
  ComponentDto toDto(final ComponentEntity entity);

  /** The stored state as a full replacement, which a patch then changes; null lists leave the stored ones alone. */
  @Mapping(target = "packages", ignore = true)
  @Mapping(target = "upgradeStops", ignore = true)
  @Mapping(target = "dependencies", ignore = true)
  ComponentRequest toRequest(final ComponentEntity entity);

  ComponentPackageDto toPackageDto(final ComponentPackageEntity entity);

  List<ComponentPackageDto> toPackageDtos(final List<ComponentPackageEntity> entities);

  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "name", source = "name")
  @Mapping(target = "description", source = "description")
  @Mapping(target = "sourceType", source = "sourceType")
  @Mapping(target = "sourceRef", source = "sourceRef")
  @Mapping(target = "aptSuite", source = "aptSuite")
  @Mapping(target = "aptComponent", source = "aptComponent")
  @Mapping(target = "aptArchitecture", source = "aptArchitecture")
  @Mapping(target = "tagPattern", source = "tagPattern")
  @Mapping(target = "versionStrategy", source = "versionStrategy", defaultValue = "SEMVER")
  @Mapping(target = "majorConstraint", source = "majorConstraint")
  @Mapping(target = "allowPrerelease", source = "allowPrerelease", defaultValue = "false")
  @Mapping(target = "maxStep", source = "maxStep", defaultValue = "MINOR")
  @Mapping(target = "upgradePathNotes", source = "upgradePathNotes")
  @Mapping(target = "upgradeDocUrl", source = "upgradeDocUrl")
  @Mapping(target = "releaseNotesUrl", source = "releaseNotesUrl")
  @Mapping(target = "needsMaintenanceWindow", source = "needsMaintenanceWindow", defaultValue = "false")
  @Mapping(target = "eolProduct", source = "eolProduct")
  @Mapping(target = "eolCycle", source = "eolCycle")
  @Mapping(target = "cpeVendor", source = "cpeVendor")
  @Mapping(target = "cpeProduct", source = "cpeProduct")
  @Mapping(target = "checkEnabled", source = "checkEnabled", defaultValue = "true")
  void updateEntity(@MappingTarget final ComponentEntity entity, final ComponentRequest request);
}
