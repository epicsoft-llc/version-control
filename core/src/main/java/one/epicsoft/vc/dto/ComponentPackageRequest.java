package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ComponentPackageRequest {

  @Nonnull
  @NotBlank
  @Size(max = 200)
  @JsonProperty("name")
  private String name;

  @Nullable
  @Schema(description = "Position in the list; left out means the position it is sent at.")
  @JsonProperty("sortOrder")
  private Integer sortOrder;

  @Nullable
  @Schema(description = "The package whose version the matrix cell shows. Without one, the first package takes the role.")
  @JsonProperty("leadingPackage")
  private Boolean leadingPackage;
}
