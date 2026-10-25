package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.EolStatusDto;
import one.epicsoft.vc.exception.AppValidationException;
import one.epicsoft.vc.facade.EolStatusFacade;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.nonNull;

/**
 * When support ends for what is running.
 *
 * <p>A component that names no product is not checked and answers with an empty
 * list. That is somebody saying the question does not apply, not a fault.
 */
@Slf4j
@RestController
@RequestMapping(value = EolStatusController.PATH_PREFIX, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "End of life", description = "When support ends for what is running, from endoflife.date.")
public class EolStatusController {

  public static final String PATH_PREFIX = "api/v1/eol-statuses";

  private final EolStatusFacade eolStatusFacade;

  @Operation(summary = "The end-of-life status of a component or of every component of a project",
             description = "One of `componentId` and `projectId` is required. A component without `eolProduct` is not checked and answers with an empty list.")
  @GetMapping
  public ResponseEntity<List<EolStatusDto>> find(@RequestParam(required = false) final UUID projectId,
                                                 @RequestParam(required = false) final UUID componentId) {
    log.debug("Incoming GET end-of-life status for project {}, component {}", projectId, componentId);
    if (nonNull(componentId)) {
      return ResponseEntity.ok(this.eolStatusFacade.findAllByComponent(componentId));
    }
    if (nonNull(projectId)) {
      return ResponseEntity.ok(this.eolStatusFacade.findAllByProjectAsDto(projectId));
    }
    throw AppValidationException.parameterRequired("projectId or componentId");
  }

  @Operation(summary = "Look up the end of life of a component now",
             description = "Asks endoflife.date right away, with its own cycle and error state, apart from the update check.")
  @PostMapping
  public ResponseEntity<List<EolStatusDto>> checkNow(@RequestParam final UUID componentId) {
    log.debug("Incoming POST end-of-life lookup for component {}", componentId);
    return ResponseEntity.ok(this.eolStatusFacade.checkAsDto(componentId));
  }
}
