package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ComponentLinkRequest {

  @Nonnull
  @NotBlank
  @Size(max = 2000)
  @Schema(description = "An http or https address; what it leads to is judged by its host.")
  @JsonProperty("url")
  private String url;
}
