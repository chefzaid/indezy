package dev.swirlit.indezy.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI features over HTTP against a stand-in for Anthropic's API: the request the SDK sends, the answer read into an
 * opportunity's fields or a message, the usage counted against the account's allowance, the member's own key, a refusal
 * and a refused key.
 */
class AiIntegrationTest extends TwoAccountsIntegrationTest {

    private static final HttpServer ANTHROPIC;
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();
    private static final List<String> KEYS = new CopyOnWriteArrayList<>();
    private static final List<String> BETAS = new CopyOnWriteArrayList<>();

    static {
        try {
            ANTHROPIC = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        ANTHROPIC.createContext("/v1/messages", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            // The SDK sends the key as a bearer token, older ones as x-api-key.
            String bearer = exchange.getRequestHeaders().getFirst("Authorization");
            String key = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : exchange.getRequestHeaders().getFirst("x-api-key");
            BODIES.add(body);
            KEYS.add(key);
            BETAS.add(String.valueOf(exchange.getRequestHeaders().getFirst("anthropic-beta")));
            int status = 200;
            String answer;
            if ("sk-ant-refused".equals(key)) {
                status = 401;
                answer = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
            } else if (body.contains("PLEASE-REFUSE")) {
                answer = message("", "refusal");
            } else if (body.contains("<posting>")) {
                answer = message("{\"role\":\"Développeur Java Senior\",\"company\":\"Conseil X\",\"companyIsIntermediary\":true,\"endClient\":\"\"," +
                    "\"dailyRate\":650,\"techStack\":[\"Java\",\" Spring Boot \",\"Java\",\"\"],\"place\":\"92400 Courbevoie\"," +
                    "\"workMode\":\"HYBRID\",\"remoteDaysPerWeek\":2,\"startDate\":\"2027-01-04\",\"durationInMonths\":0," +
                    "\"summary\":\"Refonte d'un SI de paiement.\"}", "end_turn");
            } else {
                answer = message("{\"subject\":\"Mission Java Senior\",\"body\":\"Bonjour,\\n\\nJe reviens vers vous.\\n\\nAlice\"}", "end_turn");
            }
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        ANTHROPIC.start();
    }

    private static String message(String json, String stopReason) {
        String text = json.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\",\"content\":[" +
            (json.isEmpty() ? "" : "{\"type\":\"text\",\"text\":\"" + text + "\"}") + "],\"stop_reason\":\"" + stopReason +
            "\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":1000,\"output_tokens\":200}}";
    }

    @DynamicPropertySource
    static void anthropic(DynamicPropertyRegistry registry) {
        registry.add("indezy.ai.api-key", () -> "sk-ant-indezy");
        registry.add("indezy.ai.base-url", () -> "http://127.0.0.1:" + ANTHROPIC.getAddress().getPort());
        // 1.5 cents a month: two requests of 0.8 cents go (the second starts at 0.8), the third is refused.
        registry.add("indezy.ai.account-monthly-cap-usd", () -> "0.015");
    }

    @AfterAll
    static void stop() {
        ANTHROPIC.stop(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsPostingsAndDraftsMessagesWithinTheAllowanceOrWithTheMembersKey() {
        assertThat(call(alice, HttpMethod.GET, "/ai/status", null, Map.class).getBody()).containsEntry("available", true)
            .containsEntry("ownKey", false).containsEntry("model", "claude-opus-5-5").containsEntry("limitUsd", 0.015);

        // A posting read into an opportunity's fields, through Indezy's key with the refusal fallback on.
        Map<String, Object> posting = call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "Mission Java Senior chez Conseil X..."),
            Map.class).getBody();
        assertThat(posting).containsEntry("role", "Développeur Java Senior").containsEntry("company", "Conseil X")
            .containsEntry("companyIsIntermediary", true).containsEntry("endClient", null).containsEntry("dailyRate", 650)
            .containsEntry("place", "92400 Courbevoie").containsEntry("workMode", "HYBRID").containsEntry("remoteDaysPerMonth", 8)
            .containsEntry("startDate", "2027-01-04").containsEntry("durationInMonths", null);
        assertThat((List<String>) posting.get("techStack")).containsExactly("Java", "Spring Boot");
        String sent = BODIES.getLast();
        assertThat(sent).contains("\"model\":\"claude-opus-5-5\"", "\"fallbacks\":\"default\"", "\"effort\":\"low\"", "json_schema",
            "Mission Java Senior chez Conseil X");
        assertThat(KEYS.getLast()).isEqualTo("sk-ant-indezy");
        assertThat(BETAS.getLast()).contains("server-side-fallback-2026-07-01");
        assertThat(((Number) call(alice, HttpMethod.GET, "/ai/status", null, Map.class).getBody().get("spentUsd")).doubleValue())
            .isEqualTo(0.008);

        // A message about Alice's opportunity; Bob cannot draft one about it.
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java Senior", "dailyRate", 650, "status", "INTERVIEW",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId(), "techStack", "Java, Spring"), Map.class));
        Map<String, Object> message = Map.of("projectId", project, "kind", "FOLLOW_UP", "language", "FR", "notes", "Rappeler ma dispo");
        assertThat(call(alice, HttpMethod.POST, "/ai/message", message, Map.class).getBody()).containsEntry("subject", "Mission Java Senior")
            .containsEntry("body", "Bonjour,\n\nJe reviens vers vous.\n\nAlice");
        assertThat(BODIES.getLast()).contains("a follow-up asking where the process stands", "Java Senior", "Rappeler ma dispo");
        assertThat(call(bob, HttpMethod.POST, "/ai/message", message, String.class).getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // The allowance used up: refused, until the member brings their own key, which no limit applies to.
        assertThat(call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "Une autre mission"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.GET, "/ai/status", null, Map.class).getBody()).containsEntry("available", false)
            .containsEntry("reason", "ACCOUNT_LIMIT");
        assertThat(call(alice, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "not-a-key"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "sk-ant-alice-own", "model", "claude-haiku-5-5"), Map.class).getBody())
            .containsEntry("available", true).containsEntry("ownKey", true).containsEntry("model", "claude-haiku-5-5");
        assertThat(jdbcTemplate.queryForObject("SELECT ai_api_key FROM users WHERE email = ?", String.class, aliceEmail))
            .isNotNull().doesNotContain("sk-ant-alice-own");
        assertThat(call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "Une autre mission"), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(KEYS.getLast()).isEqualTo("sk-ant-alice-own");
        assertThat(BODIES.getLast()).contains("\"model\":\"claude-haiku-5-5\"");

        // A refusal, and a key Anthropic refuses, are told as such.
        assertThat(call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "PLEASE-REFUSE"), Map.class).getBody())
            .containsEntry("message", "The AI declined this request");
        call(alice, HttpMethod.PUT, "/ai/key", Map.of("apiKey", "sk-ant-refused"), Map.class);
        assertThat(call(alice, HttpMethod.POST, "/ai/posting", Map.of("text", "Une mission"), Map.class).getBody())
            .containsEntry("message", "The AI key was refused: check it");
        assertThat(call(alice, HttpMethod.DELETE, "/ai/key", null, Map.class).getBody()).containsEntry("ownKey", false)
            .containsEntry("available", false);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ai_usage WHERE own_key = true", Integer.class)).isPositive();
    }
}
