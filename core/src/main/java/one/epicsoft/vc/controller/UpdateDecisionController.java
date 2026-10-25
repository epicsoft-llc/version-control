package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.UpdateDecisionDto;
import one.epicsoft.vc.dto.UpdateDecisionRequest;
import one.epicsoft.vc.exception.AppValidationException;
import one.epicsoft.vc.facade.UpdateDecisionFacade;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.nonNull;

/** What somebody decided about an available update, and where it is recorded. */
@Slf4j
@RestController
@RequestMapping(value = UpdateDecisionController.PATH_PREFIX,
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Update decisions", description = "What was decided about an available update — one decision per component.")
public class UpdateDecisionController {

  public static final String PATH_PREFIX = "api/v1/update-decisions";

  private final UpdateDecisionFacade updateDecisionFacade;

  @Operation(summary = "The decision of a component or of every component of a project",
             description = "One of `componentId` and `projectId` is required. No decision yet is an empty list: that is where every component starts.")
  @GetMapping(consumes = MediaType.ALL_VALUE)
  public ResponseEntity<List<UpdateDecisionDto>> find(@RequestParam(required = false) final UUID projectId,
                                                      @RequestParam(required = false) final UUID componentId) {
    log.debug("Incoming GET decisions for project {}, component {}", projectId, componentId);
    if (nonNull(componentId)) {
      return ResponseEntity.ok(this.updateDecisionFacade.findAllByComponent(componentId));
    }
    if (nonNull(projectId)) {
      return ResponseEntity.ok(this.updateDecisionFacade.findAllByProject(projectId));
    }
    throw AppValidationException.parameterRequired("projectId or componentId");
  }

  @Operation(summary = "Decide about the available update of a component",
             description = """
                 Replaces the decision of the component and records the version it is about — the newest one the last \
                 check found. A newer version reopens it. `PLANNED` and `SKIPPED` take the update off the task list, \
                 `POSTPONED` until `snoozeUntil`, which has to lie in the future. `BLOCKED` cannot be set; it comes from \
                 the dependency check.""")
  @PostMapping
  public ResponseEntity<UpdateDecisionDto> decide(@RequestBody @Valid final UpdateDecisionRequest request) {
    log.debug("Incoming POST decision for component {}", request.getComponentId());
    return ResponseEntity.ok(this.updateDecisionFacade.decide(request));
  }
}
