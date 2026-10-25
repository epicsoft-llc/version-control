package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.ComponentChangeDto;
import one.epicsoft.vc.facade.ComponentChangeFacade;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(value = ComponentChangeController.PATH_PREFIX, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Component changes", description = "What was changed on the components of a project, across all of them.")
public class ComponentChangeController {

  public static final String PATH_PREFIX = "api/v1/component-changes";

  private final ComponentChangeFacade componentChangeFacade;

  @Operation(summary = "The latest changes in a project",
             description = "Master data, decisions and rollout plans of every component, newest first; `limit` caps the list (1 to 200).")
  @GetMapping
  public ResponseEntity<List<ComponentChangeDto>> findRecentByProject(@RequestParam final UUID projectId,
                                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(200)
                                                                      final int limit) {
    log.debug("Incoming GET latest changes of project {} (limit {})", projectId, limit);
    return ResponseEntity.ok(this.componentChangeFacade.findRecentByProject(projectId, limit));
  }
}
