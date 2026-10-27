package one.epicsoft.vc.controller;

import one.epicsoft.vc.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.hamcrest.Matchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP layer itself: status codes and the shape of the error body.
 *
 * <p>Everything else goes through the facades, which leaves exactly the claims of
 * the exception handler untested — and those are the ones that were wrong twice.
 */
@AutoConfigureMockMvc
class ApiIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void answersNotFoundWithATranslatedSentence() throws Exception {
    this.mockMvc.perform(get("/api/v1/projects/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty())
                // The key itself must never reach the reader.
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.startsWith("error."))))
                .andExpect(jsonPath("$.fields").isArray());
  }

  @Test
  void answersWithTheDefaultsOfAnInstallationThatSetsNothing() throws Exception {
    this.mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debianVersions").value("SHORT"))
                .andExpect(jsonPath("$.vulnerabilities").value(false));
  }

  @Test
  void refusesALookupWithoutADependencyCheckDatabase() throws Exception {
    this.mockMvc.perform(post("/api/v1/vulnerabilities").param("componentId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.startsWith("error."))));
  }

  @Test
  void answersBadRequestForAMissingParameter() throws Exception {
    this.mockMvc.perform(get("/api/v1/components"))
                .andExpect(status().isBadRequest());
  }

  @Test
  void answersBadRequestForAnIdThatIsNotAUuid() throws Exception {
    this.mockMvc.perform(get("/api/v1/projects/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
  }

  @Test
  void answersBadRequestWhenNeitherFilterIsGiven() throws Exception {
    // Answering 200 with an empty matrix would be the hardest kind of bug to find.
    this.mockMvc.perform(get("/api/v1/component-environments"))
                .andExpect(status().isBadRequest());
  }

  @Test
  void answersBadRequestForABodyThatIsNotJson() throws Exception {
    this.mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
  }

  @Test
  void answersUnsupportedMediaTypeForTheWrongContentType() throws Exception {
    this.mockMvc.perform(post("/api/v1/projects").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void answersBadRequestWithFieldErrorsForAnInvalidBody() throws Exception {
    this.mockMvc.perform(post("/api/v1/projects")
                             .contentType(MediaType.APPLICATION_JSON)
                             .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("name"));
  }

  @Test
  void answersConflictForADuplicateName() throws Exception {
    final String body = "{\"name\":\"Duplicate %s\"}".formatted(UUID.randomUUID());

    this.mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    this.mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
  }

  @Test
  void answersNotFoundForAPathNobodyServes() throws Exception {
    this.mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound());
  }

  @Test
  void answersNotFoundForTheMatrixOfAnUnknownProject() throws Exception {
    // An empty matrix would read as a project without components.
    this.mockMvc.perform(get("/api/v1/projects/{id}/matrix", UUID.randomUUID()))
                .andExpect(status().isNotFound());
  }

  @Test
  void answersNotFoundWhenAVersionIsChangedOnAnUnknownCell() throws Exception {
    this.mockMvc.perform(post("/api/v1/version-changes")
                             .contentType(MediaType.APPLICATION_JSON)
                             .content("{\"componentEnvironmentId\":\"%s\",\"toVersion\":\"1.0\"}"
                                          .formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
  }

  @Test
  void answersBadRequestWhenTheHistoryIsAskedForWithoutAFilter() throws Exception {
    // Every change ever recorded is not a list anybody asked for.
    this.mockMvc.perform(get("/api/v1/version-changes"))
                .andExpect(status().isBadRequest());
  }

  @Test
  void answersMethodNotAllowedForTheWrongVerb() throws Exception {
    this.mockMvc.perform(post("/api/v1/projects/{id}", UUID.randomUUID())
                             .contentType(MediaType.APPLICATION_JSON)
                             .content("{}"))
                .andExpect(status().isMethodNotAllowed());
  }
}
