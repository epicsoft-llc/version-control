package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.SettingsDto;
import one.epicsoft.vc.facade.SettingsFacade;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** How this installation is set up, as far as a client needs it to show what it reads. */
@Slf4j
@RestController
@RequestMapping(value = SettingsController.PATH_PREFIX, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Settings", description = "How this installation is set up, as far as a client needs it.")
public class SettingsController {

  public static final String PATH_PREFIX = "api/v1/settings";

  private final SettingsFacade settingsFacade;

  @Operation(summary = "The settings of this installation",
             description = "Read only. They come from the configuration of the server, so changing one means restarting it.")
  @GetMapping
  public ResponseEntity<SettingsDto> get() {
    log.debug("Incoming GET settings");
    return ResponseEntity.ok(this.settingsFacade.getSettings());
  }
}
