package dev.swirlit.indezy.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.swirlit.indezy.service.WebhookDeliveryJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice's own tool is told of her workspace's events over HTTP: an opportunity added and moved, signed with the
 * webhook's secret, retried after a failure, tested on demand, and never Bob's.
 */
class WebhookIntegrationTest extends TwoAccountsIntegrationTest {

    private record Received(Map<String, String> headers, String body) {
    }

    @Autowired
    private WebhookDeliveryJob job;

    private HttpServer receiver;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger answer = new AtomicInteger(204);

    @BeforeEach
    void listen() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/hook", exchange -> {
            received.add(new Received(Map.of("event", exchange.getRequestHeaders().getFirst("X-Indezy-Event"),
                "delivery", exchange.getRequestHeaders().getFirst("X-Indezy-Delivery"),
                "timestamp", exchange.getRequestHeaders().getFirst("X-Indezy-Timestamp"),
                "signature", exchange.getRequestHeaders().getFirst("X-Indezy-Signature")),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(answer.get(), -1);
            exchange.close();
        });
        receiver.start();
    }

    @AfterEach
    void stop() {
        receiver.stop(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tellsAToolOfTheWorkspacesEventsSigned() throws Exception {
        String url = "http://127.0.0.1:" + receiver.getAddress().getPort() + "/hook";
        Map<String, Object> created = call(alice, HttpMethod.POST, "/users/webhooks", Map.of("url", url,
            "events", List.of("OPPORTUNITY_CREATED", "OPPORTUNITY_STATUS_CHANGED", "PING")), Map.class).getBody();
        String secret = (String) created.get("secret");
        Map<String, Object> webhook = (Map<String, Object>) created.get("webhook");
        long id = ((Number) webhook.get("id")).longValue();
        assertThat(secret).hasSize(64);
        assertThat((List<String>) webhook.get("events")).containsExactly("OPPORTUNITY_CREATED", "OPPORTUNITY_STATUS_CHANGED");
        assertThat(call(alice, HttpMethod.GET, "/users/webhooks", null, String.class).getBody()).doesNotContain(secret);

        // An opportunity added, then moved: two deliveries, sent by the job, each signed.
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Architecte", "dailyRate", 650, "status", "CONTACT",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        call(alice, HttpMethod.PATCH, "/projects/" + project + "/status?status=INTERVIEW", null, Map.class);
        call(alice, HttpMethod.PATCH, "/projects/" + project + "/status?status=INTERVIEW", null, Map.class);
        assertThat(job.deliverDue()).isEqualTo(2);
        assertThat(received).hasSize(2);
        ObjectMapper json = new ObjectMapper();
        Map<String, Object> first = json.readValue(received.get(0).body(), Map.class);
        assertThat(first).containsEntry("event", "OPPORTUNITY_CREATED");
        assertThat((Map<String, Object>) first.get("data")).containsEntry("projectId", (int) project).containsEntry("role", "Architecte")
            .containsEntry("status", "CONTACT").containsEntry("dailyRate", 650);
        Map<String, Object> moved = (Map<String, Object>) json.readValue(received.get(1).body(), Map.class).get("data");
        assertThat(moved).containsEntry("status", "INTERVIEW").containsEntry("previousStatus", "CONTACT");
        for (Received request : received) {
            assertThat(request.headers().get("signature")).isEqualTo("sha256=" + hmac(secret, request.headers().get("timestamp") + "." +
                request.body()));
        }
        assertThat(received.get(1).headers()).containsEntry("event", "OPPORTUNITY_STATUS_CHANGED");

        // A failure is tried again later; the test event reaches the webhook whatever it listens to.
        answer.set(500);
        assertThat(call(alice, HttpMethod.POST, "/users/webhooks/" + id + "/ping", null, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);
        job.deliverDue();
        List<Map<String, Object>> deliveries = call(alice, HttpMethod.GET, "/users/webhooks/" + id + "/deliveries", null, List.class).getBody();
        assertThat(deliveries.get(0)).containsEntry("event", "PING").containsEntry("status", "PENDING").containsEntry("attempts", 1)
            .containsEntry("responseStatus", 500).containsEntry("error", "Answered 500");
        assertThat(deliveries.get(1)).containsEntry("status", "DELIVERED").containsEntry("responseStatus", 204);
        assertThat(job.deliverDue()).isZero();
        assertThat(call(alice, HttpMethod.GET, "/users/webhooks", null, List.class).getBody()).singleElement()
            .satisfies(item -> assertThat((Map<String, Object>) item).containsEntry("consecutiveFailures", 1).containsEntry("lastStatus", 500));

        // Turned off, it is told nothing; refused: another scheme, credentials, no event, a sixth webhook; Bob kept out.
        call(alice, HttpMethod.PUT, "/users/webhooks/" + id, Map.of("url", url, "events", List.of("OPPORTUNITY_CREATED"), "active", false),
            Map.class);
        idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Other", "dailyRate", 600, "status", "CONTACT",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        assertThat((List<?>) call(alice, HttpMethod.GET, "/users/webhooks/" + id + "/deliveries", null, List.class).getBody()).hasSize(3);
        for (Map<String, Object> wrong : List.<Map<String, Object>>of(Map.of("url", "ftp://127.0.0.1/hook", "events", List.of("PING")),
            Map.of("url", "http://user:pass@127.0.0.1/hook", "events", List.of("INVOICE_PAID")), Map.of("url", url, "events", List.of("PING")))) {
            assertThat(call(alice, HttpMethod.POST, "/users/webhooks", wrong, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        for (int i = 0; i < 4; i++) {
            call(alice, HttpMethod.POST, "/users/webhooks", Map.of("url", url, "events", List.of("INVOICE_PAID")), Map.class);
        }
        assertThat(call(alice, HttpMethod.POST, "/users/webhooks", Map.of("url", url, "events", List.of("INVOICE_PAID")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(bob, HttpMethod.GET, "/users/webhooks", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.POST, "/users/webhooks/" + id + "/ping", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/users/webhooks/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/users/webhooks/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM webhook_deliveries WHERE webhook_id = ?", Integer.class, id)).isZero();
    }

    private static String hmac(String secret, String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }
}
