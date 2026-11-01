package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.epicsoft.vc.enums.LinkKind;
import one.epicsoft.vc.enums.LinkOrigin;

@Data
@NoArgsConstructor
public class ComponentLinkDto {

  @Nonnull
  @JsonProperty("url")
  private String url;

  @Nonnull
  @JsonProperty("kind")
  private LinkKind kind;

  @Nonnull
  @JsonProperty("origin")
  private LinkOrigin origin;
}
