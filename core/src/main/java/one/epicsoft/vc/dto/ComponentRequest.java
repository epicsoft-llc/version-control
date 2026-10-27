package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.enums.VersionStrategy;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "A component as a whole: the body of POST and PUT, and the fields a PATCH may name.")
public class ComponentRequest {

  @Nonnull
  @NotNull
  @Schema(description = "The project; it cannot change once the component exists.")
  @JsonProperty("projectId")
  private UUID projectId;

  @Nonnull
  @NotBlank
  @Size(max = 100)
  @Schema(description = "Unique within the project.")
  @JsonProperty("name")
  private String name;

  @Nullable
  @JsonProperty("description")
  private String description;

  @Nonnull
  @NotNull
  @JsonProperty("sourceType")
  private SourceType sourceType;

  @Nullable
  @Size(max = 500)
  @Schema(description = "What the update source is asked for — see `SourceType`. Empty for `MANUAL`.")
  @JsonProperty("sourceRef")
  private String sourceRef;

  @Nullable
  @Size(max = 50)
  @Schema(description = "`APT_REPOSITORY` only: the suite, such as `bookworm`.")
  @JsonProperty("aptSuite")
  private String aptSuite;

  @Nullable
  @Size(max = 50)
  @Schema(description = "`APT_REPOSITORY` only: the section, such as `main`.")
  @JsonProperty("aptComponent")
  private String aptComponent;

  @Nullable
  @Size(max = 50)
  @Schema(description = "`APT_REPOSITORY` only: the architecture, such as `amd64`.")
  @JsonProperty("aptArchitecture")
  private String aptArchitecture;

  @Nullable
  @Size(max = 200)
  @Schema(description = """
      Regular expression a whole tag has to match to count as a candidate. Empty derives the rule from the running \
      tag: its variant, such as `-alpine`, and its number of segments.""")
  @JsonProperty("tagPattern")
  private String tagPattern;

  @Nullable
  @Schema(description = "How the newest candidate is chosen; left out means `SEMVER`.")
  @JsonProperty("versionStrategy")
  private VersionStrategy versionStrategy;

  @Nullable
  @Size(max = 50)
  @Schema(description = """
      Only tags of this major version count, such as `17`. A newer major then appears neither as target nor as \
      `beyond` — unlike `maxStep`, which still reports it.""")
  @JsonProperty("majorConstraint")
  private String majorConstraint;

  @Nullable
  @Schema(description = "Whether prereleases count as candidates; left out means false.")
  @JsonProperty("allowPrerelease")
  private Boolean allowPrerelease;

  @Nullable
  @Schema(description = "How far a suggested step may go; left out means `MINOR`.")
  @JsonProperty("maxStep")
  private MaxStep maxStep;

  @Nullable
  @Schema(description = "What to read or do before an update.")
  @JsonProperty("upgradePathNotes")
  private String upgradePathNotes;

  @Nullable
  @Size(max = 500)
  @Schema(description = "Where the upgrade guide is.")
  @JsonProperty("upgradeDocUrl")
  private String upgradeDocUrl;

  @Nullable
  @Size(max = 500)
  @Schema(description = """
      Where the release notes of one version are, with `{version}` standing for the tag — such as \
      `https://github.com/traefik/traefik/releases/tag/{version}`. Not needed for `GITHUB_RELEASE`, whose releases \
      are linked by themselves.""")
  @JsonProperty("releaseNotesUrl")
  private String releaseNotesUrl;

  @Nullable
  @Schema(description = "Whether an update needs a maintenance window; left out means false.")
  @JsonProperty("needsMaintenanceWindow")
  private Boolean needsMaintenanceWindow;

  @Nullable
  @Size(max = 100)
  @Schema(description = "The product on endoflife.date, such as `postgresql`. Without it there is no end-of-life check.")
  @JsonProperty("eolProduct")
  private String eolProduct;

  @Nullable
  @Size(max = 50)
  @Schema(description = "The release cycle on endoflife.date, such as `17`. Empty takes the major version production runs.")
  @JsonProperty("eolCycle")
  private String eolCycle;

  @Nullable
  @Size(max = 100)
  @Schema(description = "The vendor in the CPE dictionary, such as `postgresql`; with `cpeProduct` how known vulnerabilities are looked up.")
  @JsonProperty("cpeVendor")
  private String cpeVendor;

  @Nullable
  @Size(max = 100)
  @Schema(description = "The product in the CPE dictionary, such as `postgresql`. Without vendor and product there is no lookup.")
  @JsonProperty("cpeProduct")
  private String cpeProduct;

  @Nullable
  @Schema(description = """
      Whether the scheduler checks this component; left out means true. Off stops the regular check only: a check \
      asked for by hand still runs, and an update that is already known stays on the task list until a decision \
      takes it off — `SKIPPED` then for good, as no newer version is found to reopen it.""")
  @JsonProperty("checkEnabled")
  private Boolean checkEnabled;

  @Nullable
  @Schema(description = """
      `APT_REPOSITORY` only: the packages checked together. Left out keeps the stored list, an empty list clears it, \
      any other list replaces it.""")
  @JsonProperty("packages")
  private List<@Valid ComponentPackageRequest> packages;

  @Nullable
  @Schema(description = """
      Versions that cannot be skipped on the way up. Left out keeps the stored list, an empty list clears it, any \
      other list replaces it.""")
  @JsonProperty("upgradeStops")
  private List<@Valid UpgradeStopRequest> upgradeStops;

  @Nullable
  @Schema(description = """
      What this component needs, per prerequisite and version range. Left out keeps the stored list, an empty list \
      clears it, any other list replaces it.""")
  @JsonProperty("dependencies")
  private List<@Valid ComponentDependencyRequest> dependencies;
}
