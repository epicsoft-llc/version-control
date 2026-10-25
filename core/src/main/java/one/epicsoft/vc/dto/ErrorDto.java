package one.epicsoft.vc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The one shape of every failed request.")
public class ErrorDto {

  @Nonnull
  @JsonProperty("timestamp")
  private OffsetDateTime timestamp;

  @Nonnull
  @Schema(description = "The HTTP status, repeated.")
  @JsonProperty("status")
  private Integer status;

  @Nonnull
  @Schema(description = "Translated text, ready to display, in the language `Accept-Language` asks for — English or German.")
  @JsonProperty("message")
  private String message;

  @Nonnull
  @Schema(description = "The fields at fault in a failed validation; empty otherwise, never missing.")
  @JsonProperty("fields")
  private List<FieldErrorDto> fields;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FieldErrorDto {

    @Nonnull
    @JsonProperty("field")
    private String field;

    @Nullable
    @JsonProperty("message")
    private String message;
  }
}
