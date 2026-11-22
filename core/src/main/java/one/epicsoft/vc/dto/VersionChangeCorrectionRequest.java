package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a history entry says about its change, as a correction sends it. Versions, cell and time are not part of
 * it: they are the history itself, and a wrong version is undone by a new change, not by rewriting this one.
 */
@Data
@NoArgsConstructor
@Schema(description = "The description of a recorded change: note, backup, rollback path, downtime and issues.")
public class VersionChangeCorrectionRequest {

  @Nullable
  @JsonProperty("note")
  private String note;

  @Nullable
  @Schema(description = "Whether a backup was taken first; null means false.")
  @JsonProperty("backupTaken")
  private Boolean backupTaken;

  @Nullable
  @Size(max = 500)
  @Schema(description = "Where the backup is, such as a snapshot id or a file name.")
  @JsonProperty("backupRef")
  private String backupRef;

  @Nullable
  @Schema(description = "How to go back if the new version fails.")
  @JsonProperty("rollbackPath")
  private String rollbackPath;

  @Nullable
  @Min(0)
  @JsonProperty("downtimeMinutes")
  private Integer downtimeMinutes;

  @Nullable
  @Schema(description = "What went wrong or needed fixing during the change.")
  @JsonProperty("issues")
  private String issues;
}
