package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.TaskListDto;
import one.epicsoft.vc.facade.TaskListFacade;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/** What has to be done in one project — the entry point of the application. */
@Slf4j
@RestController
@RequestMapping(value = TaskController.PATH_PREFIX, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "What has to be done in a project, in the order it should be read.")
public class TaskController {

  public static final String PATH_PREFIX = "api/v1/tasks";

  private final TaskListFacade taskListFacade;

  @Operation(summary = "The task list of a project",
             description = "Only the sections that have entries, in the order of `TaskSection`. An update somebody decided about leaves the list until a newer version shows up.")
  @GetMapping
  public ResponseEntity<TaskListDto> getByProject(@RequestParam final UUID projectId) {
    log.debug("Incoming GET task list of project {}", projectId);
    return ResponseEntity.ok(this.taskListFacade.getByProject(projectId));
  }
}
