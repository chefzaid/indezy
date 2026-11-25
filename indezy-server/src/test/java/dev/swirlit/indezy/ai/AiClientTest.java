package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.sun.net.httpserver.HttpServer;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The client of Anthropic's Messages API against a stand-in server: answers, what stops them, and what refuses them. */
class AiClientTest {

    private HttpServer server;
    private AiClient client;
    private volatile int status = 200;
    private volatile String answer = "";
    private volatile String sent = "";

    private static String message(String content, String stopReason) {
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-5-5\",\"content\":[" + content +
            "],\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":12,\"output_tokens\":5}}";
    }

    private static String text(String value) {
        return "{\"type\":\"text\",\"text\":\"" + value + "\"}";
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        client = new AiClient(new AiProperties("sk-ant-test", null, "http://127.0.0.1:" + server.getAddress().getPort(), 10, 100));
    }

    @AfterEach
    void stop() {
        client.forget("sk-ant-test");
        server.stop(0);
    }

    private AiClient.Answer<String> chat() {
        return client.chat("sk-ant-test", "claude-sonnet-5-5", "You help.", List.of(new AiClient.Turn(true, "Bonjour"),
            new AiClient.Turn(false, "Bonjour !"), new AiClient.Turn(true, "Mon TJM ?")), 500, OutputConfig.Effort.LOW);
    }

    @Test
    void answersAConversationWithItsTextBlocksJoinedAndTheTokensItUsed() {
        answer = message(text("Visez 650 €.") + "," + text("Selon le marché."), "end_turn");

        AiClient.Answer<String> reply = chat();

        assertThat(reply.value()).isEqualTo("Visez 650 €.\n\nSelon le marché.");
        assertThat(reply.inputTokens()).isEqualTo(12);
        assertThat(reply.outputTokens()).isEqualTo(5);
        // The turns in order, the AI's own as the assistant's, and the safety fallback asked for.
        assertThat(sent).contains("\"role\":\"assistant\"", "Mon TJM ?", "\"fallbacks\":\"default\"");
    }

    @Test
    void saysWhenTheAnswerIsEmptyCutShortOrDeclined() {
        answer = message("", "end_turn");
        assertThatThrownBy(this::chat).isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("no answer");
        answer = message(text("Visez"), "max_tokens");
        assertThatThrownBy(this::chat).isInstanceOf(InvalidStateException.class).hasMessageContaining("cut short");
        answer = message(text(""), "refusal");
        assertThatThrownBy(this::chat).isInstanceOf(InvalidStateException.class).hasMessageContaining("declined");
    }

    @Test
    void tellsARefusedKeyFromAServiceThatFails() {
        answer = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        status = 401;
        assertThatThrownBy(this::chat).isInstanceOf(InvalidStateException.class).hasMessageContaining("key was refused");
        status = 403;
        assertThatThrownBy(this::chat).isInstanceOf(InvalidStateException.class).hasMessageContaining("key was refused");
        answer = "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}";
        status = 400;
        assertThatThrownBy(this::chat).isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("unavailable");
        status = 403;
        assertThatThrownBy(() -> client.structured("sk-ant-test", "claude-haiku-5-5", "Sort.", "Bonjour", Verdict.class, 100,
            OutputConfig.Effort.LOW)).isInstanceOf(InvalidStateException.class).hasMessageContaining("key was refused");
    }

    @Test
    void readsAStructuredAnswerAndSaysWhenTheServiceCannotBeReached() {
        answer = message(text("{\\\"fits\\\":true}"), "end_turn");
        AiClient.Answer<Verdict> verdict = client.structured("sk-ant-test", "claude-haiku-5-5", "Sort.", "Une mission Java", Verdict.class, 100,
            OutputConfig.Effort.LOW);
        assertThat(verdict.value().fits()).isTrue();
        assertThat(verdict.outputTokens()).isEqualTo(5);

        server.stop(0);
        assertThatThrownBy(() -> client.structured("sk-ant-test", "claude-haiku-5-5", "Sort.", "Bonjour", Verdict.class, 100,
            OutputConfig.Effort.LOW)).isInstanceOf(ServiceUnavailableException.class);
    }

    /** A structured answer's shape. */
    record Verdict(boolean fits) {
    }
}
