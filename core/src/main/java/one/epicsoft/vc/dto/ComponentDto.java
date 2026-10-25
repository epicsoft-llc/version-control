package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.enums.VersionStrategy;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class ComponentDto {

  @Nonnull
  @JsonProperty("id")
  private UUID id;

  @Nonnull
  @JsonProperty("projectId")
  private UUID projectId;

  @Nonnull
  @JsonProperty("name")
  private String name;

  @Nullable
  @JsonProperty("description")
  private String description;

  @Nonnull
  @JsonProperty("sourceType")
  private SourceType sourceType;

  @Nullable
  @JsonProperty("sourceRef")
  private String sourceRef;

  @Nullable
  @JsonProperty("aptSuite")
  private String aptSuite;

  @Nullable
  @JsonProperty("aptComponent")
  private String aptComponent;

  @Nullable
  @JsonProperty("aptArchitecture")
  private String aptArchitecture;

  @Nullable
  @JsonProperty("tagPattern")
  private String tagPattern;

  @Nonnull
  @JsonProperty("versionStrategy")
  private VersionStrategy versionStrategy;

  @Nullable
  @JsonProperty("majorConstraint")
  private String majorConstraint;

  @Nonnull
  @JsonProperty("allowPrerelease")
  private Boolean allowPrerelease;

  @Nonnull
  @JsonProperty("maxStep")
  private MaxStep maxStep;

  @Nullable
  @JsonProperty("upgradePathNotes")
  private String upgradePathNotes;

  @Nullable
  @JsonProperty("upgradeDocUrl")
  private String upgradeDocUrl;

  @Nullable
  @JsonProperty("releaseNotesUrl")
  private String releaseNotesUrl;

  @Nonnull
  @JsonProperty("needsMaintenanceWindow")
  private Boolean needsMaintenanceWindow;

  @Nullable
  @JsonProperty("eolProduct")
  private String eolProduct;

  @Nullable
  @JsonProperty("eolCycle")
  private String eolCycle;

  @Nonnull
  @JsonProperty("checkEnabled")
  private Boolean checkEnabled;

  /** Empty for everything that is not an APT component; never null. */
  @Nonnull
  @JsonProperty("packages")
  private List<ComponentPackageDto> packages;

  /** Versions that cannot be skipped on the way up. */
  @Nonnull
  @JsonProperty("upgradeStops")
  private List<UpgradeStopDto> upgradeStops;

  /** What this component needs; the other direction is evaluated, not stored. */
  @Nonnull
  @JsonProperty("dependencies")
  private List<ComponentDependencyDto> dependencies;
}
