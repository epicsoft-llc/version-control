package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.epicsoft.vc.enums.MatrixCellState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One component in one environment, as the matrix shows it. */
@Data
@NoArgsConstructor
public class MatrixCellDto {

  @Nonnull
  @JsonProperty("environmentId")
  private UUID environmentId;

  /** Null while the pair has no record — there is nothing to open yet. */
  @Nullable
  @JsonProperty("componentEnvironmentId")
  private UUID componentEnvironmentId;

  @Nonnull
  @JsonProperty("state")
  private MatrixCellState state;

  @Nullable
  @JsonProperty("currentVersion")
  private String currentVersion;

  @Nullable
  @JsonProperty("lastChangedAt")
  private Instant lastChangedAt;

  /** The version of a planned rollout; missing while nothing is planned. */
  @Nullable
  @JsonProperty("plannedVersion")
  private String plannedVersion;

  @Nullable
  @JsonProperty("plannedFor")
  private LocalDate plannedFor;

  /** latest, stable, 17-alpine — allowed, but worth a small warning. */
  @Nonnull
  @JsonProperty("movingTag")
  private Boolean movingTag;

  /** True when this version differs from the other environments of the row. */
  @Nonnull
  @JsonProperty("deviating")
  private Boolean deviating;
}
