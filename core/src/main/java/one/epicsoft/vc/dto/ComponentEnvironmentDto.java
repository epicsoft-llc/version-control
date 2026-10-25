package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The version of one component in one environment. */
@Data
@NoArgsConstructor
public class ComponentEnvironmentDto {

  @Nonnull
  @JsonProperty("id")
  private UUID id;

  @Nonnull
  @JsonProperty("componentId")
  private UUID componentId;

  @Nonnull
  @JsonProperty("environmentId")
  private UUID environmentId;

  @Nonnull
  @Schema(description = "False: the component is deliberately not present in this environment.")
  @JsonProperty("active")
  private Boolean active;

  @Nullable
  @JsonProperty("currentVersion")
  private String currentVersion;

  @Nullable
  @JsonProperty("currentDigest")
  private String currentDigest;

  @Nullable
  @JsonProperty("lastChangedAt")
  private Instant lastChangedAt;

  @Nullable
  @Schema(description = "The version a rollout is planned to; missing while nothing is planned.")
  @JsonProperty("plannedVersion")
  private String plannedVersion;

  @Nullable
  @Schema(description = "The day of the planned rollout.")
  @JsonProperty("plannedFor")
  private LocalDate plannedFor;

  @Nullable
  @JsonProperty("plannedNote")
  private String plannedNote;

  @Nonnull
  @Schema(description = "Active, but no version recorded — a gap, which an inactive cell is not.")
  @JsonProperty("versionUnknown")
  private Boolean versionUnknown;

  @Nonnull
  @Schema(description = "The version is a tag that moves, such as `latest`, `stable` or `17-alpine`: allowed, but worth a warning.")
  @JsonProperty("movingTag")
  private Boolean movingTag;

  @Nonnull
  @JsonProperty("packages")
  private List<ComponentEnvironmentPackageDto> packages;
}
