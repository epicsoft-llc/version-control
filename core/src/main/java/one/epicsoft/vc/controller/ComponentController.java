package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.ComponentChangeDto;
import one.epicsoft.vc.dto.ComponentDto;
import one.epicsoft.vc.dto.ComponentRequest;
import one.epicsoft.vc.dto.DependencyCheckDto;
import one.epicsoft.vc.dto.UpgradeProposalDto;
import one.epicsoft.vc.facade.ComponentChangeFacade;
import one.epicsoft.vc.facade.ComponentFacade;
import one.epicsoft.vc.facade.DependencyFacade;
import one.epicsoft.vc.facade.UpgradeProposalFacade;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(value = ComponentController.PATH_PREFIX,
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Components", description = "What is tracked per project: master data, update source, version rule, upgrade path and dependencies.")
public class ComponentController {

  public static final String PATH_PREFIX = "api/v1/components";
  public static final String MERGE_PATCH_JSON = "application/merge-patch+json";

  private static final String REPLACE_DESCRIPTION = """
      Every field is replaced. A field left out or `null` is cleared or falls back to its default: `versionStrategy` \
      SEMVER, `maxStep` MINOR, `allowPrerelease` and `needsMaintenanceWindow` false, `checkEnabled` true.

      The lists `packages`, `upgradeStops` and `dependencies` work differently: left out or `null` keeps the stored \
      list, an empty list clears it, any other list replaces it. To change single fields, use PATCH.
      """;

  private static final String PATCH_DESCRIPTION = """
      JSON merge patch (RFC 7386) with the fields of `ComponentRequest`, every one of them optional. A field that is \
      present is changed, a field that is left out keeps its value, and `null` clears it or resets it to its default \
      as in PUT. A list replaces the stored one as a whole; `null` or an empty list clears it.

      A field the component does not have is refused rather than ignored. Name, project and validation rules are the \
      same as for PUT. Accepts `application/merge-patch+json` and `application/json`.
      """;

  private final ComponentFacade componentFacade;
  private final UpgradeProposalFacade upgradeProposalFacade;
  private final DependencyFacade dependencyFacade;
  private final ComponentChangeFacade componentChangeFacade;

  @Operation(summary = "List the components of a project")
  @GetMapping(consumes = MediaType.ALL_VALUE)
  public ResponseEntity<List<ComponentDto>> findAllByProject(@RequestParam final UUID projectId) {
    log.debug("Incoming GET 'api/v1/components?projectId={}'", projectId);
    return ResponseEntity.ok(this.componentFacade.findAllByProject(projectId));
  }

  @Operation(summary = "Get a component")
  @GetMapping(path = "/{id}", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<ComponentDto> getById(@PathVariable final UUID id) {
    log.debug("Incoming GET 'api/v1/components/{}'", id);
    return ResponseEntity.ok(this.componentFacade.getById(id));
  }

  @Operation(summary = "What to do next in each environment",
             description = """
                 The permissible target within `maxStep`, a larger jump beyond it as a note without urgency, the versions \
                 that cannot be skipped on the way, what blocks the target, and what to read first.""")
  @GetMapping(path = "/{id}/proposals", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<List<UpgradeProposalDto>> getProposals(@PathVariable final UUID id) {
    log.debug("Incoming GET proposals for component {}", id);
    return ResponseEntity.ok(this.upgradeProposalFacade.getByComponent(id));
  }

  @Operation(summary = "Dependencies, evaluated per environment",
             description = """
                 What this component needs, or with `dependents=true` what needs this component — the direction that says \
                 whether an update of this one breaks anything.""")
  @GetMapping(path = "/{id}/dependencies", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<List<DependencyCheckDto>> getDependencies(@PathVariable final UUID id,
                                                                  @RequestParam(defaultValue = "false")
                                                                  final boolean dependents) {
    log.debug("Incoming GET dependencies of component {} (dependents={})", id, dependents);
    return ResponseEntity.ok(dependents ? this.dependencyFacade.checkDependents(id)
                                        : this.dependencyFacade.checkComponent(id));
  }

  @Operation(summary = "What was changed on a component",
             description = "Fields of its master data, decisions and rollout plans, newest first. Version changes have their own history.")
  @GetMapping(path = "/{id}/changes", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<List<ComponentChangeDto>> getChanges(@PathVariable final UUID id) {
    log.debug("Incoming GET changes of component {}", id);
    return ResponseEntity.ok(this.componentChangeFacade.findAllByComponent(id));
  }

  @Operation(summary = "Create a component")
  @PostMapping
  public ResponseEntity<ComponentDto> create(@RequestBody @Valid final ComponentRequest request) {
    log.debug("Incoming POST 'api/v1/components' with: {}", request);
    return ResponseEntity.ok(this.componentFacade.create(request));
  }

  @Operation(summary = "Replace a component", description = REPLACE_DESCRIPTION)
  @PutMapping("/{id}")
  public ResponseEntity<ComponentDto> update(@PathVariable final UUID id,
                                             @RequestBody @Valid final ComponentRequest request) {
    log.debug("Incoming PUT 'api/v1/components/{}' with: {}", id, request);
    return ResponseEntity.ok(this.componentFacade.update(id, request));
  }

  @Operation(summary = "Change single fields of a component",
             description = PATCH_DESCRIPTION,
             requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                 required = true,
                 content = {@Content(mediaType = MERGE_PATCH_JSON, schema = @Schema(implementation = ComponentRequest.class)),
                            @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ComponentRequest.class))}))
  @PatchMapping(path = "/{id}", consumes = {MERGE_PATCH_JSON, MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<ComponentDto> patch(@PathVariable final UUID id, @RequestBody final JsonNode patch) {
    // The id only, like the other writes with free text in them.
    log.debug("Incoming PATCH 'api/v1/components/{}'", id);
    return ResponseEntity.ok(this.componentFacade.patch(id, patch));
  }

  @Operation(summary = "Delete a component", description = "Its records per environment and the dependencies it declares go with it.")
  @DeleteMapping(path = "/{id}", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<Void> delete(@PathVariable final UUID id) {
    log.debug("Incoming DELETE 'api/v1/components/{}'", id);
    this.componentFacade.delete(id);
    return ResponseEntity.noContent().build();
  }
}
