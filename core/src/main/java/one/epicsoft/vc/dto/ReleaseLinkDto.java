package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A version on the way to the target, with where its release notes are.")
public class ReleaseLinkDto {

  @Nonnull
  @JsonProperty("version")
  private String version;

  @Nullable
  @Schema(description = "The release notes; missing where the component names no `releaseNotesUrl` and is no `GITHUB_RELEASE`.")
  @JsonProperty("url")
  private String url;
}
