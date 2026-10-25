package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "One change to a component: a field of its master data, a decision or a rollout plan.")
public class ComponentChangeDto {

  @Nonnull
  @JsonProperty("id")
  private UUID id;

  @Nonnull
  @JsonProperty("componentId")
  private UUID componentId;

  @Nonnull
  @JsonProperty("componentName")
  private String componentName;

  @Nonnull
  @JsonProperty("changedAt")
  private Instant changedAt;

  @Nonnull
  @Schema(description = """
      What changed: a field of `ComponentRequest` by its name, `created` for a new component, `decision` for the \
      decision about updates, or `rolloutPlan` for a planned rollout.""")
  @JsonProperty("field")
  private String field;

  @Nullable
  @Schema(description = "The value before; missing where there was none. A list is written as its entries, separated by commas.")
  @JsonProperty("oldValue")
  private String oldValue;

  @Nullable
  @Schema(description = "The value after; missing where the field was cleared.")
  @JsonProperty("newValue")
  private String newValue;

  @Nullable
  @Schema(description = "The note of a decision or of a rollout plan.")
  @JsonProperty("note")
  private String note;
}
