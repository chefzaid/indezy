package dev.swirlit.indezy.mail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendMailerTest {

    private static final Email EMAIL = new Email("alice@example.com", "1 new offer: Java", "<p>Java</p>", "Java",
        Map.of("List-Unsubscribe-Post", "List-Unsubscribe=One-Click"), "saved-search/4/1792742400");

    private HttpServer server;
    private String base;
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private volatile int status = 200;
    private volatile String answer = "{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            received.put("method", exchange.getRequestMethod());
            received.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
            received.put("idempotency", exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            received.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange);
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsTheEmailWithTheKeyAndItsIdempotencyKey() throws Exception {
        String id = mailer("re_secret").send(EMAIL);

        assertThat(id).isEqualTo("49a3999c-0ce1-4ea6-ab68-afcd6dc2e794");
        assertThat(received).containsEntry("method", "POST").containsEntry("authorization", "Bearer re_secret")
            .containsEntry("idempotency", "saved-search/4/1792742400");
        JsonNode body = JsonMapper.builder().build().readTree(received.get("body"));
        assertThat(body.path("from").asString()).isEqualTo("Indezy <noreply@swirlit.dev>");
        assertThat(body.path("to").get(0).asString()).isEqualTo("alice@example.com");
        assertThat(body.path("subject").asString()).isEqualTo("1 new offer: Java");
        assertThat(body.path("html").asString()).isEqualTo("<p>Java</p>");
        assertThat(body.path("text").asString()).isEqualTo("Java");
        assertThat(body.path("headers").path("List-Unsubscribe-Post").asString()).isEqualTo("List-Unsubscribe=One-Click");
    }

    @Test
    void reportsARefusalByItsNameOnly() {
        status = 422;
        answer = "{\"statusCode\":422,\"name\":\"validation_error\",\"message\":\"Invalid `to` field: alice@example.com\"}";

        assertThatThrownBy(() -> mailer("re_secret").send(EMAIL)).isInstanceOf(MailException.class)
            .hasMessage("Resend answered 422: validation_error");
        status = 500;
        answer = "<html>oops</html>";
        assertThatThrownBy(() -> mailer("re_secret").send(EMAIL)).hasMessage("Resend answered 500: unreadable answer");
        status = 200;
        answer = "{}";
        assertThatThrownBy(() -> mailer("re_secret").send(EMAIL)).hasMessage("Resend answered without an email id");
    }

    @Test
    void sendsNothingWithoutAKeyOrWhenResendIsUnreachable() {
        assertThat(mailer(" ").configured()).isFalse();
        assertThatThrownBy(() -> mailer(null).send(EMAIL)).isInstanceOf(MailException.class).hasMessage("No Resend key is set");
        assertThat(received).isEmpty();
        server.stop(0);
        assertThatThrownBy(() -> mailer("re_secret").send(EMAIL)).isInstanceOf(MailException.class).hasMessageStartingWith("Resend could not be reached");
    }

    private ResendMailer mailer(String key) {
        return new ResendMailer(new MailProperties(key, "Indezy <noreply@swirlit.dev>", "https://indezy.example", base));
    }

    private void respond(HttpExchange exchange) throws IOException {
        byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
