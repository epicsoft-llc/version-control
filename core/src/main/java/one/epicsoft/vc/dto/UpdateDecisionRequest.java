package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.epicsoft.vc.enums.DecisionState;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a decision about an available update. BLOCKED is not accepted here:
 * it is derived from the dependency check, and setting it by hand would claim a
 * blocker that nothing points at.
 */
@Data
@NoArgsConstructor
public class UpdateDecisionRequest {

  @Nonnull
  @NotNull
  @JsonProperty("componentId")
  private UUID componentId;

  @Nonnull
  @NotNull
  @Schema(description = "Any state but `BLOCKED`, which comes from the dependency check.")
  @JsonProperty("state")
  private DecisionState state;

  @Nullable
  @Schema(description = "Required for `POSTPONED`, and in the future: the update returns as `SNOOZE_EXPIRED` once it has passed.")
  @JsonProperty("snoozeUntil")
  private Instant snoozeUntil;

  @Nullable
  @Schema(description = "Why — kept when a newer version reopens the decision.")
  @JsonProperty("note")
  private String note;

  @Nullable
  @Size(max = 500)
  @Schema(description = "The release notes the decision is based on.")
  @JsonProperty("releaseUrl")
  private String releaseUrl;
}
