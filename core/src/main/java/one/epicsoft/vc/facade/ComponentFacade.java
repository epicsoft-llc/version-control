package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentPackageRequest;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.ComponentDependencyRequest;
import one.epicsoft.vc.dto.UpgradeStopRequest;
import one.epicsoft.vc.exception.AppConflictException;
import one.epicsoft.vc.exception.AppValidationException;
import one.epicsoft.vc.jpa.entity.ComponentEntity;
import one.epicsoft.vc.jpa.entity.ComponentPackageEntity;
import one.epicsoft.vc.jpa.entity.ComponentDependencyEntity;
import one.epicsoft.vc.jpa.entity.UpgradeStopEntity;
import one.epicsoft.vc.jpa.entity.ProjectEntity;
import one.epicsoft.vc.mapper.ComponentMapper;
import one.epicsoft.vc.mapper.ComponentDependencyMapper;
import one.epicsoft.vc.mapper.UpgradeStopMapper;
import one.epicsoft.vc.service.ComponentEnvironmentService;
import one.epicsoft.vc.service.ComponentService;
import one.epicsoft.vc.service.ComponentDependencyService;
import one.epicsoft.vc.service.UpgradeStopService;
import one.epicsoft.vc.version.ConstraintEvaluator;
import one.epicsoft.vc.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.nonBlank;
import static one.epicsoft.vc.util.VcUtil.nonEquals;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentFacade {

  private final ComponentService componentService;
  private final ProjectService projectService;
  private final ComponentEnvironmentService componentEnvironmentService;
  private final ComponentMapper componentMapper;
  private final UpgradeStopService upgradeStopService;
  private final UpgradeStopMapper upgradeStopMapper;
  private final ComponentDependencyService componentDependencyService;
  private final ComponentDependencyMapper componentDependencyMapper;
  private final ConstraintEvaluator constraintEvaluator;
  private final UpdateCheckFacade updateCheckFacade;
  private final JsonMapper jsonMapper;
  private final Validator validator;

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public List<ComponentDto> findAllByProject(@Nonnull final UUID projectId) {
    return this.toDtos(this.componentService.findAllByProject(projectId));
  }

  @Nonnull
  @Transactional(readOnly = true, rollbackFor = Throwable.class)
  public ComponentDto getById(@Nonnull final UUID id) {
    return this.toDtos(List.of(this.componentService.getById(id))).getFirst();
  }

  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public ComponentDto create(@Nonnull final ComponentRequest request) {
    final ProjectEntity project = this.projectService.getById(request.getProjectId());
    if (this.componentService.existsByProjectAndName(project.getId(), request.getName())) {
      throw AppConflictException.duplicateName("Component", request.getName());
    }
    final ComponentEntity entity = new ComponentEntity();
    this.componentMapper.updateEntity(entity, request);
    normalise(entity);
    entity.setProject(project);
    final ComponentEntity saved = this.componentService.save(entity);
    this.applyUpgradeStops(saved, request.getUpgradeStops());
    this.applyDependencies(saved, request.getDependencies());
    this.applyPackages(saved, request.getPackages());
    return this.toDtos(List.of(saved)).getFirst();
  }

  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public ComponentDto update(@Nonnull final UUID id, @Nonnull final ComponentRequest request) {
    final ComponentEntity entity = this.componentService.getById(id);
    if (nonEquals(entity.getProjectId(), request.getProjectId())) {
      // Silently ignoring it would answer 200 while the component stayed put.
      throw AppValidationException.projectCannotChange("Component", id);
    }
    if (nonEquals(entity.getName(), request.getName())
        && this.componentService.existsByProjectAndName(entity.getProjectId(), request.getName())) {
      throw AppConflictException.duplicateName("Component", request.getName());
    }
    final List<Object> ruleBefore = ruleOf(entity);
    this.componentMapper.updateEntity(entity, request);
    normalise(entity);
    final ComponentEntity saved = this.componentService.save(entity);
    this.applyUpgradeStops(saved, request.getUpgradeStops());
    this.applyDependencies(saved, request.getDependencies());
    this.applyPackages(saved, request.getPackages());
    if (nonEquals(ruleBefore, ruleOf(saved))) {
      this.updateCheckFacade.reapplyRule(saved.getId());
    }
    return this.toDtos(List.of(saved)).getFirst();
  }

  /**
   * Changes the fields the patch names and keeps the rest — JSON merge patch, RFC 7386. The merged state takes
   * the same way as a replacement, so both hold the same rules.
   */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public ComponentDto patch(@Nonnull final UUID id, @Nonnull final JsonNode patch) {
    final ComponentEntity entity = this.componentService.getById(id);
    if (!patch.isObject()) {
      throw AppValidationException.patchNotApplicable("Component", id, "the root");
    }
    // Merged as JSON, not through the setters: a null would reach a setter that refuses it, and the answer would
    // lose the field the validation below names.
    final ObjectNode merged = this.jsonMapper.valueToTree(this.componentMapper.toRequest(entity));
    patch.properties().forEach(field -> {
      if (field.getValue().isNull()) {
        merged.remove(field.getKey());
      } else {
        merged.set(field.getKey(), field.getValue());
      }
    });
    final ComponentRequest request;
    try {
      // Unknown fields fail here although the API ignores them elsewhere: a misspelt name would otherwise
      // answer 200 for a change that never happened.
      request = this.jsonMapper.readerFor(ComponentRequest.class)
                               .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                               .readValue(merged);
    } catch (final JacksonException exception) {
      throw AppValidationException.patchNotApplicable("Component", id, exception.getPathReference());
    }
    clearNullLists(request, patch);
    final Set<ConstraintViolation<ComponentRequest>> violations = this.validator.validate(request);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
    return this.update(id, request);
  }

  /** In a merge patch null removes. A null list keeps the stored one in a replacement, so it becomes empty here. */
  private static void clearNullLists(@Nonnull final ComponentRequest request, @Nonnull final JsonNode patch) {
    if (isNullValue(patch, "packages")) {
      request.setPackages(List.of());
    }
    if (isNullValue(patch, "upgradeStops")) {
      request.setUpgradeStops(List.of());
    }
    if (isNullValue(patch, "dependencies")) {
      request.setDependencies(List.of());
    }
  }

  private static boolean isNullValue(@Nonnull final JsonNode patch, @Nonnull final String field) {
    return patch.has(field) && patch.get(field).isNull();
  }

  /** What decides which candidate wins; a change here reads the last check again. */
  @Nonnull
  private static List<Object> ruleOf(@Nonnull final ComponentEntity component) {
    return Arrays.asList(component.getTagPattern(), component.getMajorConstraint(), component.isAllowPrerelease(),
                         component.getVersionStrategy());
  }

  /** Takes the version records with it; the package rows stay until the component is removed for good. */
  @Transactional(rollbackFor = Throwable.class)
  public void delete(@Nonnull final UUID id) {
    final ComponentEntity component = this.componentService.getById(id);
    // What this component declared goes with it — it is not history. What other
    // components need from it stays theirs; every query hides those rows while
    // this one is deleted.
    this.componentDependencyService.deleteAllOf(id);
    this.componentEnvironmentService.findAllByComponent(id).forEach(this.componentEnvironmentService::delete);
    this.componentService.delete(component);
  }

  /**
   * Null leaves the stored packages alone, a list replaces them. An empty list is
   * therefore how a component loses its packages — distinct from "not mentioned".
   */
  private void applyPackages(@Nonnull final ComponentEntity component,
                             @Nullable final List<ComponentPackageRequest> requested) {
    if (isNull(requested)) {
      return;
    }
    final List<ComponentPackageEntity> packages = new ArrayList<>();
    final Set<String> names = new HashSet<>();
    int position = 0;
    for (final ComponentPackageRequest entry : requested) {
      if (!names.add(entry.getName())) {
        // Otherwise this reaches the unique index and answers 500, one level below
        // the component name that is already guarded.
        throw AppValidationException.duplicatePackage(entry.getName());
      }
      final ComponentPackageEntity entity = new ComponentPackageEntity();
      entity.setName(entry.getName());
      entity.setSortOrder(nonNull(entry.getSortOrder()) ? entry.getSortOrder() : position);
      entity.setLeadingPackage(nonNull(entry.getLeadingPackage()) ? entry.getLeadingPackage() : Boolean.FALSE);
      packages.add(entity);
      position++;
    }
    this.markLeadingPackage(packages);
    this.componentService.replacePackages(component, packages);
  }

  /**
   * Null leaves the stored dependencies alone, a list replaces them.
   *
   * <p>A component cannot depend on itself, and a prerequisite has to live in
   * the same project: a dependency across projects would be evaluated against
   * environments that do not exist on the other side.
   */
  private void applyDependencies(@Nonnull final ComponentEntity component,
                                 @Nullable final List<ComponentDependencyRequest> requested) {
    if (isNull(requested)) {
      return;
    }
    final List<ComponentDependencyEntity> dependencies = new ArrayList<>();
    final Set<UUID> seen = new HashSet<>();
    for (final ComponentDependencyRequest entry : requested) {
      if (component.getId().equals(entry.getDependsOnId())) {
        throw AppValidationException.selfDependency(component.getId());
      }
      if (!seen.add(entry.getDependsOnId())) {
        throw AppValidationException.duplicateDependency(entry.getDependsOnId());
      }
      if (!this.constraintEvaluator.isReadable(entry.getVersionRange())) {
        // Stored unchecked, it would block every upgrade of the prerequisite for
        // good, with nothing but a detail string to explain why.
        throw AppValidationException.invalidVersionRange(entry.getVersionRange());
      }
      final ComponentEntity dependsOn = this.componentService.getById(entry.getDependsOnId());
      if (nonEquals(component.getProjectId(), dependsOn.getProjectId())) {
        throw AppValidationException.dependencyAcrossProjects(component.getId(), dependsOn.getId());
      }
      final ComponentDependencyEntity dependency = new ComponentDependencyEntity();
      dependency.setDependsOn(dependsOn);
      dependency.setVersionRange(entry.getVersionRange());
      dependency.setNote(entry.getNote());
      dependencies.add(dependency);
    }
    this.componentDependencyService.replaceDependencies(component, dependencies);
  }

  /**
   * A cleared text field arrives as an empty string, and an empty string is not
   * a product. Left as it is, the end-of-life job would pick this component on
   * every tick, do nothing, write no row — and no other component would ever be
   * looked up again.
   */
  private static void normalise(@Nonnull final ComponentEntity component) {
    if (!nonBlank(component.getEolProduct())) {
      component.setEolProduct(null);
    }
    if (!nonBlank(component.getEolCycle())) {
      component.setEolCycle(null);
    }
  }

  /**
   * Null leaves the stored stops alone, a list replaces them — same shape as the
   * package list, because they are edited in the same form.
   */
  private void applyUpgradeStops(@Nonnull final ComponentEntity component,
                                 @Nullable final List<UpgradeStopRequest> requested) {
    if (isNull(requested)) {
      return;
    }
    final List<UpgradeStopEntity> stops = new ArrayList<>();
    final Set<String> versions = new HashSet<>();
    for (final UpgradeStopRequest entry : requested) {
      if (!versions.add(entry.getVersion())) {
        // Otherwise this reaches the unique index and answers 500.
        throw AppValidationException.duplicateUpgradeStop(entry.getVersion());
      }
      final UpgradeStopEntity stop = new UpgradeStopEntity();
      stop.setVersion(entry.getVersion());
      stop.setNote(entry.getNote());
      stops.add(stop);
    }
    this.upgradeStopService.replaceStops(component, stops);
  }

  /**
   * The matrix cell of a multi package component shows the leading one, and the
   * version dialog writes it there. Without a package carrying the flag the cell
   * would stay empty for good, however many versions are recorded — so the first
   * package in the configured order takes the role until someone picks another.
   */
  private void markLeadingPackage(@Nonnull final List<ComponentPackageEntity> packages) {
    if (packages.isEmpty() || packages.stream().anyMatch(ComponentPackageEntity::isLeadingPackage)) {
      return;
    }
    packages.stream()
            .min(Comparator.comparing(ComponentPackageEntity::getSortOrder))
            .ifPresent(entry -> entry.setLeadingPackage(Boolean.TRUE));
  }

  /** One query for the packages of all components instead of one per component. */
  @Nonnull
  private List<ComponentDto> toDtos(@Nonnull final List<ComponentEntity> entities) {
    final Map<UUID, List<ComponentPackageEntity>> packages =
        this.componentService.findPackages(entities.stream().map(ComponentEntity::getId).toList()).stream()
            .collect(Collectors.groupingBy(ComponentPackageEntity::getComponentId));

    final Map<UUID, List<UpgradeStopEntity>> stops =
        this.upgradeStopService.findAllByComponents(entities.stream().map(ComponentEntity::getId).toList()).stream()
            .collect(Collectors.groupingBy(UpgradeStopEntity::getComponentId));

    final Map<UUID, List<ComponentDependencyEntity>> dependencies =
        this.componentDependencyService.findAllByComponents(entities.stream().map(ComponentEntity::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(ComponentDependencyEntity::getComponentId));

    return entities.stream().map(entity -> {
      final ComponentDto dto = this.componentMapper.toDto(entity);
      dto.setPackages(this.componentMapper.toPackageDtos(packages.getOrDefault(entity.getId(), List.of())));
      dto.setUpgradeStops(this.upgradeStopMapper.toDtos(stops.getOrDefault(entity.getId(), List.of())));
      dto.setDependencies(this.componentDependencyMapper.toDtos(
          dependencies.getOrDefault(entity.getId(), List.of())));
      return dto;
    }).toList();
  }
}
