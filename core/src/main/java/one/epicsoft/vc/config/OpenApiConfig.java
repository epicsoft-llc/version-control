package one.epicsoft.vc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Objects.requireNonNullElse;

/**
 * The description served under {@code /api/v1/openapi.json}. Paths, parameters and schemas come from the
 * controllers and DTOs; what they cannot express — the rules that hold across all of them — is written here.
 */
@Configuration
public class OpenApiConfig {

  private static final String DESCRIPTION = """
      Inventory of the versions each component runs in each environment, with update and end-of-life checks.

      - Every path is under `/api/v1`; requests and responses are JSON.
      - A field without a value is left out of a response rather than sent as `null`.
      - Every error has the same shape (`ErrorDto`): the HTTP status, a translated message and, for a failed \
      validation, the fields at fault.
      - A recorded version changes only through `POST /api/v1/version-changes`, which writes the history entry in \
      the same step.
      - The application has no login of its own; access is meant to be restricted in front of it.
      """;

  @Nonnull
  @Bean
  public OpenAPI openApi() {
    // The jar manifest carries the release; a build from the IDE or a test has none.
    final String version = requireNonNullElse(OpenApiConfig.class.getPackage().getImplementationVersion(), "development");
    return new OpenAPI().info(new Info().title("Version Control API")
                                        .version(version)
                                        .description(DESCRIPTION)
                                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
  }
}
