package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import one.epicsoft.vc.config.DisplayProperties;
import one.epicsoft.vc.dto.SettingsDto;
import org.springframework.stereotype.Service;

/** The settings of this installation as a client sees them. They come from the configuration, not from the database. */
@Service
@RequiredArgsConstructor
public class SettingsFacade {

  private final DisplayProperties displayProperties;

  @Nonnull
  public SettingsDto getSettings() {
    final SettingsDto settings = new SettingsDto();
    settings.setDebianVersions(this.displayProperties.getDebianVersions());
    return settings;
  }
}
