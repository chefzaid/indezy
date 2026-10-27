package dev.swirlit.indezy.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The served OpenAPI description stays complete: summaries, statuses, error answers and examples that fit. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OpenApiDocumentation documentation;

    private JsonNode docs;

    @BeforeEach
    void readTheDescription() throws Exception {
        docs = new ObjectMapper().readTree(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
    }

    @Test
    void describesTheRunningApiOnItsOwnHost() {
        assertThat(docs.at("/info/version").asText()).isNotBlank().isNotEqualTo("1.0.0");
        assertThat(docs.at("/servers/0/url").asText()).isEqualTo("/api");
        assertThat(docs.toString()).doesNotContain("indezy.com");
    }

    @Test
    void servesTheSwaggerUi() throws Exception {
        assertThat(mockMvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void everyOperationHasASummaryAndTheErrorsItCanAnswer() {
        List<String> missing = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> paths = docs.get("paths").fields(); paths.hasNext();) {
            Map.Entry<String, JsonNode> path = paths.next();
            for (Iterator<Map.Entry<String, JsonNode>> operations = path.getValue().fields(); operations.hasNext();) {
                Map.Entry<String, JsonNode> operation = operations.next();
                String name = operation.getKey().toUpperCase() + " " + path.getKey();
                JsonNode responses = operation.getValue().get("responses");
                if (operation.getValue().path("summary").asText().isBlank()) {
                    missing.add(name + " summary");
                }
                boolean open = path.getKey().startsWith("/auth/") || path.getKey().startsWith("/public/");
                if (!open && (!responses.has("401") || !responses.has("403"))) {
                    missing.add(name + " 401/403");
                }
                if (path.getKey().contains("{") && !responses.has("404")) {
                    missing.add(name + " 404");
                }
                responses.fields().forEachRemaining(response -> {
                    if (response.getKey().startsWith("4")) {
                        assertThat(response.getValue().at("/content/application~1json/schema/$ref").asText())
                            .as(name).endsWith("/ErrorResponse");
                    }
                });
            }
        }
        assertThat(missing).isEmpty();
        assertThat(docs.at("/components/schemas/ErrorResponse/properties").has("validationErrors")).isTrue();
    }

    @Test
    void statesTheStatusEachOperationAnswersOnSuccess() {
        assertThat(docs.at("/paths/~1clients/post/responses").has("201")).isTrue();
        assertThat(docs.at("/paths/~1clients/post/responses").has("200")).isFalse();
        assertThat(docs.at("/paths/~1clients~1{id}/delete/responses").has("204")).isTrue();
        assertThat(docs.at("/paths/~1projects~1{projectId}~1notes/post/responses").has("201")).isTrue();
        assertThat(docs.at("/paths/~1users~1onboarding~1complete/post/responses").has("204")).isTrue();
        assertThat(docs.at("/paths/~1clients~1{id}/get/responses").has("200")).isTrue();
    }

    @Test
    void everyExampleFitsItsSchema() {
        assertThat(documentation.examples()).containsKeys("ProjectDto", "ClientDto", "ContactDto", "LoginResponse", "ErrorResponse");
        documentation.examples().keySet().forEach(name -> {
            JsonNode schema = docs.at("/components/schemas/" + name);
            assertThat(schema.isMissingNode()).as(name + " is a schema").isFalse();
            JsonNode example = schema.get("example");
            assertThat(example).as(name + " example").isNotNull();
            example.fields().forEachRemaining(field -> {
                JsonNode property = schema.at("/properties/" + field.getKey());
                assertThat(property.isMissingNode()).as(name + "." + field.getKey() + " is a property").isFalse();
                if (property.has("enum")) {
                    assertThat(property.get("enum").toString()).as(name + "." + field.getKey())
                        .contains("\"" + field.getValue().asText() + "\"");
                }
            });
            schema.path("required").forEach(required ->
                assertThat(example.has(required.asText())).as(name + " example has " + required.asText()).isTrue());
        });
    }
}
