package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.EnvironmentDto;
import one.epicsoft.vc.dto.EnvironmentRequest;
import one.epicsoft.vc.facade.EnvironmentFacade;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(value = EnvironmentController.PATH_PREFIX,
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Environments", description = "The columns of a project's matrix — test, staging, production and the like.")
public class EnvironmentController {

  public static final String PATH_PREFIX = "api/v1/environments";

  private final EnvironmentFacade environmentFacade;

  @Operation(summary = "List the environments of a project", description = "In their configured order.")
  @GetMapping(consumes = MediaType.ALL_VALUE)
  public ResponseEntity<List<EnvironmentDto>> findAllByProject(@RequestParam final UUID projectId) {
    log.debug("Incoming GET 'api/v1/environments?projectId={}'", projectId);
    return ResponseEntity.ok(this.environmentFacade.findAllByProject(projectId));
  }

  @Operation(summary = "Get an environment")
  @GetMapping(path = "/{id}", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<EnvironmentDto> getById(@PathVariable final UUID id) {
    log.debug("Incoming GET 'api/v1/environments/{}'", id);
    return ResponseEntity.ok(this.environmentFacade.getById(id));
  }

  @Operation(summary = "Create an environment")
  @PostMapping
  public ResponseEntity<EnvironmentDto> create(@RequestBody @Valid final EnvironmentRequest request) {
    log.debug("Incoming POST 'api/v1/environments' with: {}", request);
    return ResponseEntity.ok(this.environmentFacade.create(request));
  }

  @Operation(summary = "Replace an environment", description = "Every field is replaced: `sortOrder` falls back to 0, `production` to false.")
  @PutMapping("/{id}")
  public ResponseEntity<EnvironmentDto> update(@PathVariable final UUID id,
                                               @RequestBody @Valid final EnvironmentRequest request) {
    log.debug("Incoming PUT 'api/v1/environments/{}' with: {}", id, request);
    return ResponseEntity.ok(this.environmentFacade.update(id, request));
  }

  @Operation(summary = "Delete an environment", description = "The versions recorded in it go with it.")
  @DeleteMapping(path = "/{id}", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<Void> delete(@PathVariable final UUID id) {
    log.debug("Incoming DELETE 'api/v1/environments/{}'", id);
    this.environmentFacade.delete(id);
    return ResponseEntity.noContent().build();
  }
}
