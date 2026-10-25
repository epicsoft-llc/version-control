package one.epicsoft.vc.controller;

import com.jayway.jsonpath.JsonPath;
import one.epicsoft.vc.AbstractIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static java.util.Objects.nonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The machine readable description of the API.
 *
 * <p>Paths and schemas follow the code by themselves. What does not is the part written by hand — summaries,
 * the meaning of each enum value, the error shape — so these tests hold every operation and every enum to it,
 * including the ones added later.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class OpenApiIntegrationTest extends AbstractIntegrationTest {

  private static final String PATH = "/api/v1/openapi.json";

  @Autowired
  private MockMvc mockMvc;

  @Test
  void servesTheDescriptionUnderTheApiPath() throws Exception {
    // Under /api, so the dashboard container forwards it like any other call.
    this.mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").value(Matchers.startsWith("3.1")))
                .andExpect(jsonPath("$.info.title").value("Version Control API"))
                .andExpect(jsonPath("$.info.license.name").value("MIT"));
  }

  @Test
  void looksUpNoneOfItsTextsAsAMessageKey(final CapturedOutput output) throws Exception {
    // springdoc hands every text to the MessageSource unless told not to, and ours logs each unknown key as an
    // error — a few hundred lines per request. A language no other test asks for makes this request build the
    // description itself instead of reading it from the cache.
    this.mockMvc.perform(get(PATH).header("Accept-Language", "fr-CH")).andExpect(status().isOk());

    assertFalse(output.getAll().contains("No message for key"), "a text of the description was looked up as a message key");
  }

  @Test
  void describesOnlyTheApi() throws Exception {
    final Map<String, Object> paths = JsonPath.read(this.description(), "$.paths");

    assertFalse(paths.isEmpty());
    paths.keySet().forEach(path -> assertTrue(path.startsWith("/api/v1/"), path));
  }

  @Test
  void givesEveryOperationASummaryAndTheErrorShape() throws Exception {
    final Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(this.description(), "$.paths");

    paths.forEach((path, operations) -> operations.forEach((method, operation) -> {
      final String name = "%s %s".formatted(method, path);
      assertTrue(operation.get("summary") instanceof final String summary && !summary.isBlank(), name);
      final String error = JsonPath.read(operation, "$.responses.default.content['application/json'].schema['$ref']");
      assertEquals("#/components/schemas/ErrorDto", error, name);
    }));
  }

  @Test
  void explainsEveryValueOfEveryEnum() throws Exception {
    final Map<String, Map<String, Object>> schemas = JsonPath.read(this.description(), "$.components.schemas");
    final List<String> enums = schemas.keySet().stream().filter(name -> schemas.get(name).containsKey("enum")).toList();

    // DecisionState is the one that was missing when this test was written.
    assertTrue(enums.contains("DecisionState"), enums.toString());
    enums.forEach(name -> {
      final String description = (String) schemas.get(name).get("description");
      final List<String> values = JsonPath.read(schemas.get(name), "$.enum");
      assertTrue(nonNull(description) && !description.isBlank(), name);
      values.forEach(value -> assertTrue(description.contains("`%s`".formatted(value)), "%s.%s".formatted(name, value)));
    });
  }

  @Test
  void describesThePatchOfAComponentAsAMergePatch() throws Exception {
    final Map<String, Object> patch = JsonPath.read(this.description(), "$.paths['/api/v1/components/{id}'].patch");

    assertTrue(((String) patch.get("description")).contains("RFC 7386"));
    final Map<String, Object> media = JsonPath.read(patch, "$.requestBody.content");
    assertTrue(media.containsKey(ComponentController.MERGE_PATCH_JSON), media.keySet().toString());
    assertTrue(media.containsKey(MediaType.APPLICATION_JSON_VALUE), media.keySet().toString());
  }

  private String description() throws Exception {
    return this.mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }
}
