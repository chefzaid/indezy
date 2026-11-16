package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The network as it happens, over a real stream: Bob's page hears of Alice's message, of her typing and of a like,
 * never their text; Alice sees when Bob read her message while both show read receipts; a visitor without a token, or
 * an account away from the network, opens no stream.
 */
class NetworkLiveIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Stream<String>> opened = new ArrayList<>();

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void closeStreams() {
        opened.forEach(Stream::close);
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private HttpResponse<Stream<String>> open(String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url("/network/stream"))).header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(10));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<Stream<String>> response = client.send(request.build(), HttpResponse.BodyHandlers.ofLines());
        opened.add(response.body());
        return response;
    }

    /** The stream's lines as they come, read on a thread of their own. */
    private BlockingQueue<String> lines(HttpResponse<Stream<String>> response) {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> {
            try {
                response.body().forEach(lines::add);
            } catch (RuntimeException e) {
                lines.add("closed");
            }
        });
        reader.setDaemon(true);
        reader.start();
        return lines;
    }

    /** The data line of the next event of that name. */
    private static String next(BlockingQueue<String> lines, String event) throws InterruptedException {
        boolean named = false;
        while (true) {
            String line = lines.poll(5, TimeUnit.SECONDS);
            assertThat(line).as("event " + event).isNotNull();
            if (line.equals("event:" + event)) {
                named = true;
            } else if (named && line.startsWith("data:")) {
                return line.substring("data:".length());
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void tellsWhatChangesAsItHappens() throws Exception {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        Number conversation = (Number) call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class)
            .getBody().get("id");

        HttpResponse<Stream<String>> stream = open(bob.token());
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
        BlockingQueue<String> bobHears = lines(stream);
        assertThat(next(bobHears, "ready")).isEqualTo("{}");

        // A message: which conversation, never its text.
        call(alice, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "Une mission Kafka ?"), Map.class);
        String message = next(bobHears, "message");
        assertThat(message).contains("\"conversationId\":" + conversation).doesNotContain("Kafka");

        // Alice typing, by her first name.
        call(alice, HttpMethod.POST, "/network/conversations/" + conversation + "/typing", null, Void.class);
        assertThat(next(bobHears, "typing")).contains("\"name\":\"alice\"");

        // Bob reads: Alice sees it while both show read receipts.
        call(bob, HttpMethod.PUT, "/network/conversations/" + conversation + "/read", null, Map.class);
        assertThat(next(bobHears, "read")).contains("\"conversationId\":" + conversation);
        Map<String, Object> seen = (Map<String, Object>) call(alice, HttpMethod.GET, "/network/conversations/" + conversation, null, Map.class)
            .getBody().get("conversation");
        assertThat(seen.get("seenAt")).isNotNull();
        assertThat(call(bob, HttpMethod.GET, "/network/messages/settings", null, Map.class).getBody())
            .containsEntry("readReceipts", true).containsEntry("typing", true);
        assertThat(call(bob, HttpMethod.PUT, "/network/messages/settings", Map.of("readReceipts", false), Map.class).getBody())
            .containsEntry("readReceipts", false).containsEntry("typing", true);
        Map<String, Object> hidden = (Map<String, Object>) call(alice, HttpMethod.GET, "/network/conversations/" + conversation, null,
            Map.class).getBody().get("conversation");
        assertThat(hidden.get("seenAt")).isNull();

        // A notification.
        Object post = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Retour sur Kafka"), Map.class).getBody().get("id");
        call(alice, HttpMethod.PUT, "/network/posts/" + post + "/like", null, Map.class);
        assertThat(next(bobHears, "notice")).isEqualTo("{\"type\":\"notice\"}");

        // No token, or away from the network: no stream.
        assertThat(open(null).statusCode()).isEqualTo(401);
        Account carol = register("carol");
        assertThat(open(carol.token()).statusCode()).isNotEqualTo(200);
    }
}
