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

@Data
@NoArgsConstructor
public class EnvironmentRequest {

  @Nonnull
  @NotNull
  @JsonProperty("projectId")
  private UUID projectId;

  @Nonnull
  @NotBlank
  @Size(max = 100)
  @JsonProperty("name")
  private String name;

  @Nullable
  @Schema(description = "Position of the column in the matrix; left out means 0.")
  @JsonProperty("sortOrder")
  private Integer sortOrder;

  @Nullable
  @Schema(description = """
      Whether this is production; left out means false. A moving tag here is a task, and the end-of-life check reads \
      the version from here first.""")
  @JsonProperty("production")
  private Boolean production;
}
