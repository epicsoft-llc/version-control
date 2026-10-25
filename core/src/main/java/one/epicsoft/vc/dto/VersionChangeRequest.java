package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/**
 * What the dialog "change version" sends: the new state and the story around it,
 * saved in one step. Everything but the target version is optional, but all of it
 * is on the form — asked for later, none of it would ever be filled in.
 *
 * <p>A component with several packages sends {@code packages} instead of
 * {@code toVersion}; the entry that comes out of it is still a single one,
 * because note, backup and rollback belong to the action, not to a package.
 */
@Data
@NoArgsConstructor
@Schema(description = "A new version and the story around it, saved as one history entry.")
public class VersionChangeRequest {

  @Nonnull
  @NotNull
  @Schema(description = "The cell — component in environment — whose version changes.")
  @JsonProperty("componentEnvironmentId")
  private UUID componentEnvironmentId;

  @Nullable
  @Size(max = 200)
  @Schema(description = "The new version. Required unless the component is maintained per package; then `packages` carries it.")
  @JsonProperty("toVersion")
  private String toVersion;

  // The annotation belongs on the type argument: on the list itself Hibernate
  // Validator warns that the form is deprecated.
  @Nullable
  @Schema(description = "For a component maintained per package: the new version of each package that changes.")
  @JsonProperty("packages")
  private List<@Valid VersionChangePackageRequest> packages;

  @Nullable
  @JsonProperty("note")
  private String note;

  /** The checkbox starts unchecked and is meant to nag. */
  @Nullable
  @Schema(description = "Whether a backup was taken first; left out means false.")
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
