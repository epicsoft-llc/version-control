package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One thing to do, with what it takes to judge it without opening anything. */
@Data
@NoArgsConstructor
public class TaskEntryDto {

  @Nonnull
  @JsonProperty("componentId")
  private UUID componentId;

  @Nonnull
  @JsonProperty("componentName")
  private String componentName;

  /** The environment this is about, where it is about one. */
  @Nullable
  @JsonProperty("environmentName")
  private String environmentName;

  @Nullable
  @JsonProperty("currentVersion")
  private String currentVersion;

  @Nullable
  @JsonProperty("latestVersion")
  private String latestVersion;

  /**
   * Free text of the reader's own making — a note somebody wrote, or the
   * technical message of a failed check. Never a sentence composed here: a
   * sentence built in the backend cannot be translated, and the rule against
   * hardcoded visible text covers backend messages as much as templates.
   */
  @Nullable
  @JsonProperty("detail")
  private String detail;

  /**
   * The constraints this entry is about — violated ones for a dependency, the
   * blocking ones for an update that cannot be done. The interface composes the
   * sentence from them, in the reader's language.
   */
  @Nonnull
  @JsonProperty("constraints")
  private List<DependencyCheckDto> constraints;

  @Nonnull
  @Schema(description = "The CVE identifiers behind a `VULNERABLE` entry, exploited first; empty in every other section.")
  @JsonProperty("vulnerabilities")
  private List<String> vulnerabilities;

  @Nullable
  @JsonProperty("since")
  private Instant since;
}
