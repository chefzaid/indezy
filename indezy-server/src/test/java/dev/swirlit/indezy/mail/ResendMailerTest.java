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
import java.util.List;
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
            // A read has no idempotency key: only the headers sent are kept.
            java.util.Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization")).ifPresent(value -> received.put("authorization", value));
            java.util.Optional.ofNullable(exchange.getRequestHeaders().getFirst("Idempotency-Key")).ifPresent(value -> received.put("idempotency", value));
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
    void sendsAMembersEmailInTheirNameWithAnswersToThem() throws Exception {
        mailer("re_secret").send(new Email("lea@acme.example", "Mission Java", "<p>Bonjour</p>", "Bonjour", Map.of(), "contact-email-1",
            "Alice Martin", "alice@example.com", List.of()));
        JsonNode body = JsonMapper.builder().build().readTree(received.get("body"));
        assertThat(body.path("from").asString()).isEqualTo("\"Alice Martin via Indezy\" <noreply@swirlit.dev>");
        assertThat(body.path("reply_to").get(0).asString()).isEqualTo("alice@example.com");
        assertThat(received.get("body")).doesNotContain("\"reply_to\":null");
        assertThat(body.has("attachments")).isFalse();
    }

    @Test
    void attachesFilesInBase64() throws Exception {
        mailer("re_secret").send(new Email("lea@acme.example", "Dispo", "<p>Bonjour</p>", "Bonjour", Map.of(), "contact-email-2",
            "Alice Martin", "alice@example.com", List.of()).withAttachments(List.of(new Email.Attachment("dossier.pdf", "application/pdf",
            "%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))));
        JsonNode file = JsonMapper.builder().build().readTree(received.get("body")).path("attachments").get(0);
        assertThat(file.path("filename").asString()).isEqualTo("dossier.pdf");
        assertThat(file.path("content").asString()).isEqualTo("JVBERg==");
        assertThat(file.path("content_type").asString()).isEqualTo("application/pdf");
    }

    @Test
    void keepsTheSendersNameFromBreakingTheAddress() {
        assertThat(ResendMailer.from("Indezy <noreply@swirlit.dev>", "Eve \"<evil@x>\"\r\nBcc: a@b")).isEqualTo(
            "\"Eve evil@x Bcc: a@b via Indezy\" <noreply@swirlit.dev>");
        assertThat(ResendMailer.from("noreply@swirlit.dev", "Alice")).isEqualTo("\"Alice via Indezy\" <noreply@swirlit.dev>");
        assertThat(ResendMailer.from("Indezy <noreply@swirlit.dev>", " ")).isEqualTo("Indezy <noreply@swirlit.dev>");
        assertThat(ResendMailer.from("Indezy <noreply@swirlit.dev>", null)).isEqualTo("Indezy <noreply@swirlit.dev>");
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

    @Test
    void readsAReceivedEmailWithTheReadingKeyItsSenderSubjectAndText() throws Exception {
        answer = "{\"id\":\"in-1\",\"subject\":\"Re: Mission Java\",\"from\":\"lea@acme.example\"," +
            "\"headers\":{\"from\":\"Léa Martin <lea@acme.example>\"},\"text\":\"Bonjour, appelons-nous jeudi.\"}";
        ResendMailer reader = new ResendMailer(new MailProperties("re_send", "Indezy <noreply@swirlit.dev>", "https://indezy.example", base,
            "replies.example", "whsec_x", "re_read"));

        ResendMailer.Received email = reader.received("in-1");

        assertThat(email).isEqualTo(new ResendMailer.Received("Léa Martin <lea@acme.example>", "Re: Mission Java", "Bonjour, appelons-nous jeudi."));
        assertThat(received).containsEntry("method", "GET").containsEntry("authorization", "Bearer re_read");
    }

    @Test
    void readsTheTextOfAnHtmlOnlyEmailWithoutItsScriptsAndStylesAndFallsBackOnTheEnvelopeSender() throws Exception {
        answer = "{\"subject\":\"Dispo ?\",\"from\":\"paul@esn.example\",\"html\":\"<style>p{color:red}</style>" +
            "<p>Bonjour,</p><p>Dispo&nbsp;en janvier ?<br>Paul</p><script>x()</script>\"}";

        ResendMailer.Received email = mailer("re_secret").received("in-2");

        assertThat(email.from()).isEqualTo("paul@esn.example");
        assertThat(email.text()).isEqualTo("Bonjour,\n Dispo en janvier ?\nPaul");
        assertThat(email.text()).doesNotContain("color", "x()");
    }

    @Test
    void refusesToReadAnUnsafeIdOrWithoutAKeyAndTellsWhatResendAnswered() {
        assertThatThrownBy(() -> mailer("re_secret").received("../emails")).isInstanceOf(MailException.class);
        assertThatThrownBy(() -> mailer("re_secret").received(null)).isInstanceOf(MailException.class);
        assertThatThrownBy(() -> mailer("").received("in-3")).isInstanceOf(MailException.class);
        assertThat(received).isEmpty();
        status = 404;
        answer = "{\"name\":\"not_found\"}";
        assertThatThrownBy(() -> mailer("re_secret").received("in-4")).isInstanceOf(MailException.class).hasMessageContaining("404")
            .hasMessageContaining("not_found");
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
