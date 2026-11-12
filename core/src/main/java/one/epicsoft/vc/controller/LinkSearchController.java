package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.LinkSearchDto;
import one.epicsoft.vc.facade.ComponentLinkFacade;
import one.epicsoft.vc.facade.LinkSearchFacade;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(value = LinkSearchController.PATH_PREFIX, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Links", description = "Where a component lives: links kept by hand, derived from its own fields, and found by the link search.")
public class LinkSearchController {

  public static final String PATH_PREFIX = "api/v1/link-searches";

  private final ComponentLinkFacade componentLinkFacade;
  private final LinkSearchFacade linkSearchFacade;

  @Operation(summary = "Every link of a component",
             description = """
                 Kept by hand, derived from the source, the end-of-life product and the upgrade guide, and found by the \
                 link search, each address once. A component never searched answers with what it has, without a search \
                 state.""")
  @GetMapping
  public ResponseEntity<LinkSearchDto> find(@RequestParam final UUID componentId) {
    log.debug("Incoming GET links of component {}", componentId);
    return ResponseEntity.ok(this.componentLinkFacade.findByComponent(componentId));
  }

  @Operation(summary = "Search the links of a component now",
             description = """
                 Asks endoflife.date, Docker Hub and GitHub right away, also for a component searched a moment ago. The \
                 next scheduled search counts from this one.""")
  @PostMapping
  public ResponseEntity<LinkSearchDto> searchNow(@RequestParam final UUID componentId) {
    log.debug("Incoming POST link search for component {}", componentId);
    return ResponseEntity.ok(this.linkSearchFacade.searchNow(componentId));
  }
}
