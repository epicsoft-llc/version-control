package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/**
 * What can be done in one environment, and what merely exists beyond it.
 *
 * <p>The two are separate on purpose. Only {@code target} is something to act on;
 * {@code beyond} is a note that a larger jump is available and needs planning.
 * Showing the second as a demand would leave a permanent red state that nobody
 * can resolve, and people stop reading those.
 */
@Data
@NoArgsConstructor
public class UpgradeProposalDto {

  @Nonnull
  @JsonProperty("environmentId")
  private UUID environmentId;

  @Nonnull
  @JsonProperty("environmentName")
  private String environmentName;

  @Nullable
  @JsonProperty("currentVersion")
  private String currentVersion;

  @Nullable
  @Schema(description = "The highest version `maxStep` allows from here — the one to act on.")
  @JsonProperty("target")
  private String target;

  @Nullable
  @Schema(description = "A newer version past the limit: a note without urgency, never a task.")
  @JsonProperty("beyond")
  private String beyond;

  @Nonnull
  @Schema(description = "The components that need this one at a range the target would leave. Empty means nothing does.")
  @JsonProperty("blockedBy")
  private List<DependencyCheckDto> blockedBy;

  @Nonnull
  @Schema(description = "Versions between here and the target that cannot be skipped.")
  @JsonProperty("stops")
  private List<UpgradeStopDto> stops;

  /** From the component: what to read and whether a window is needed. */
  @Nullable
  @JsonProperty("upgradePathNotes")
  private String upgradePathNotes;

  @Nullable
  @JsonProperty("upgradeDocUrl")
  private String upgradeDocUrl;

  @Nonnull
  @JsonProperty("needsMaintenanceWindow")
  private Boolean needsMaintenanceWindow;
}
