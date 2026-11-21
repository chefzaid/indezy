package dev.swirlit.indezy.push;

import com.sun.net.httpserver.HttpServer;
import dev.swirlit.indezy.mail.MailProperties;
import dev.swirlit.indezy.model.PushSubscription;
import dev.swirlit.indezy.repository.PushSubscriptionRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The sender against a stand-in push service: the headers it sends, a body only the browser reads, and a gone subscription. */
class PushSenderTest {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsAnEncryptedSignedMessageAndDropsAGoneSubscription() throws Exception {
        List<Map<String, String>> headers = new ArrayList<>();
        List<byte[]> bodies = new ArrayList<>();
        AtomicInteger status = new AtomicInteger(201);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            headers.add(Map.of("encoding", exchange.getRequestHeaders().getFirst("Content-Encoding"),
                "ttl", exchange.getRequestHeaders().getFirst("TTL"), "authorization", exchange.getRequestHeaders().getFirst("Authorization")));
            bodies.add(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();

        String[] vapid = WebPushCrypto.newVapidKeys();
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        PushService push = new PushService(new PushProperties(vapid[0], vapid[1]), subscriptions, mock(UserRepository.class),
            new SyncTaskExecutor(), JsonMapper.builder().build(), Clock.systemUTC(), new MailProperties(null, "x", "https://indezy.example", "x"));
        KeyPair browser = WebPushCrypto.generate();
        byte[] browserKey = WebPushCrypto.point((ECPublicKey) browser.getPublic());
        byte[] secret = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/push");
        subscription.setP256dh(URL.encodeToString(browserKey));
        subscription.setAuth(URL.encodeToString(secret));

        push.send(subscription, "Indezy", "You have a new notification on the network.", "/network/notifications");

        assertThat(headers).singleElement().satisfies(sent -> {
            assertThat(sent).containsEntry("encoding", "aes128gcm").containsEntry("ttl", "86400");
            assertThat(sent.get("authorization")).startsWith("vapid t=").endsWith(", k=" + vapid[0]);
        });
        String payload = new String(WebPushCrypto.decrypt(bodies.getFirst(), (ECPrivateKey) browser.getPrivate(), browserKey, secret),
            StandardCharsets.UTF_8);
        assertThat(payload).contains("\"title\":\"Indezy\"", "You have a new notification on the network.",
            "\"operation\":\"navigateLastFocusedOrOpen\"", "\"url\":\"/network/notifications\"");
        verify(subscriptions, never()).deleteByEndpoint(subscription.getEndpoint());

        status.set(410);
        push.send(subscription, "Indezy", "Again", "/network/notifications");
        verify(subscriptions).deleteByEndpoint(subscription.getEndpoint());
    }
}
