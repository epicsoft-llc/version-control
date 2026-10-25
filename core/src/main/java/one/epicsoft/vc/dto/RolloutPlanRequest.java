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
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Schema(description = "A rollout planned for one environment. It ends by itself once the version, or a newer one, is recorded.")
public class RolloutPlanRequest {

  @Nonnull
  @NotBlank
  @Size(max = 200)
  @Schema(description = "The version to roll out; it cannot be the one already recorded.")
  @JsonProperty("plannedVersion")
  private String plannedVersion;

  @Nonnull
  @NotNull
  @Schema(description = "The day of the rollout. Once it has passed with the version still missing, the plan is `ROLLOUT_OVERDUE`.")
  @JsonProperty("plannedFor")
  private LocalDate plannedFor;

  @Nullable
  @Schema(description = "Why, or what has to happen first — shown on the task list.")
  @JsonProperty("note")
  private String note;
}
