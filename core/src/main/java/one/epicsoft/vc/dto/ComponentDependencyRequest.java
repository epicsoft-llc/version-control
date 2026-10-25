package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** One dependency, as the master data form sends it. */
@Data
@NoArgsConstructor
public class ComponentDependencyRequest {

  @Nonnull
  @NotNull
  @Schema(description = "The prerequisite, a component of the same project.")
  @JsonProperty("dependsOnId")
  private UUID dependsOnId;

  @Nonnull
  @NotBlank
  @Size(max = 200)
  @Schema(description = "The versions of the prerequisite that work, as an npm style range such as `>=14 <18`.")
  @JsonProperty("versionRange")
  private String versionRange;

  @Nullable
  @Schema(description = "Where the requirement was read.")
  @JsonProperty("note")
  private String note;
}
