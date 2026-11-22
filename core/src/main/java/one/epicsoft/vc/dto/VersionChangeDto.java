package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** One entry of the change history. */
@Data
@NoArgsConstructor
public class VersionChangeDto {

  @Nonnull
  @JsonProperty("id")
  private UUID id;

  @Nonnull
  @JsonProperty("componentEnvironmentId")
  private UUID componentEnvironmentId;

  /** Carried along so the history of a component can name the environment. */
  @Nonnull
  @JsonProperty("environmentId")
  private UUID environmentId;

  @Nonnull
  @JsonProperty("componentId")
  private UUID componentId;

  /** Null for the first entry of a cell — there was nothing before it. */
  @Nullable
  @JsonProperty("fromVersion")
  private String fromVersion;

  @Nonnull
  @JsonProperty("toVersion")
  private String toVersion;

  @Nonnull
  @JsonProperty("changedAt")
  private Instant changedAt;

  @Nullable
  @JsonProperty("note")
  private String note;

  @Nonnull
  @JsonProperty("backupTaken")
  private Boolean backupTaken;

  @Nullable
  @JsonProperty("backupRef")
  private String backupRef;

  @Nullable
  @JsonProperty("rollbackPath")
  private String rollbackPath;

  @Nullable
  @JsonProperty("downtimeMinutes")
  private Integer downtimeMinutes;

  @Nullable
  @JsonProperty("issues")
  private String issues;

  @Nullable
  @Schema(description = "When note, backup, rollback path, downtime or issues were last corrected; null while the entry reads as recorded.")
  @JsonProperty("correctedAt")
  private Instant correctedAt;
}
