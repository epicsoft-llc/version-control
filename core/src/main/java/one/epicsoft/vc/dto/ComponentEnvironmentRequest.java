package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Creates or updates the record of a component in an environment. The deployed
 * version is not changed here — that happens in the dedicated dialog, which
 * writes a history entry in the same step.
 */
@Data
@NoArgsConstructor
@Schema(description = "The cell of a component in an environment; component and environment have to belong to the same project.")
public class ComponentEnvironmentRequest {

  @Nonnull
  @NotNull
  @JsonProperty("componentId")
  private UUID componentId;

  @Nonnull
  @NotNull
  @JsonProperty("environmentId")
  private UUID environmentId;

  @Nullable
  @Schema(description = "False: the component is deliberately not present in this environment. Left out keeps the cell as it is; a new cell starts active.")
  @JsonProperty("active")
  private Boolean active;

  @Nullable
  @Size(max = 200)
  @Schema(description = "The first version of a cell that has none. A recorded version changes through `POST /api/v1/version-changes`.")
  @JsonProperty("currentVersion")
  private String currentVersion;
}
