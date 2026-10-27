package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.epicsoft.vc.enums.DebianVersionDisplay;

/** What a client has to know about this installation before it shows a version. */
@Data
@NoArgsConstructor
public class SettingsDto {

  @Nonnull
  @JsonProperty("debianVersions")
  @Schema(description = "How Debian package versions are written; set with `VC_DISPLAY_DEBIAN_VERSIONS`.")
  private DebianVersionDisplay debianVersions;

  @Nonnull
  @JsonProperty("vulnerabilities")
  @Schema(description = "Whether a Dependency-Check database is configured (`VC_VULNERABILITIES_DB_URL`); without one there are no known vulnerabilities to show.")
  private Boolean vulnerabilities;
}
