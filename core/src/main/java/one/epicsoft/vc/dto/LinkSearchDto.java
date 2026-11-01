package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.epicsoft.vc.enums.CheckState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class LinkSearchDto {

  @Nonnull
  @JsonProperty("componentId")
  private UUID componentId;

  @Nonnull
  @Schema(description = """
      Kept by hand, derived from the component's own fields and found by the link search — each address once, the one \
      kept by hand winning; ordered by kind: repositories, images, packages, website, documentation, support policy, \
      end of life, others.""")
  @JsonProperty("links")
  private List<ComponentLinkDto> links;

  @Nullable
  @Schema(description = "When the link search last ran; absent while it never has.")
  @JsonProperty("checkedAt")
  private Instant checkedAt;

  @Nullable
  @Schema(description = "`ERROR` when a site did not answer; the links it found before stay. Absent while never searched.")
  @JsonProperty("checkState")
  private CheckState checkState;

  @Nullable
  @JsonProperty("errorMessage")
  private String errorMessage;
}
