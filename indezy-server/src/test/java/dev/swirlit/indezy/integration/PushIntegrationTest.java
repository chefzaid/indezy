package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.push.WebPushCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.GeneralSecurityException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Web push over HTTP: the key to subscribe with, a browser's subscription kept and forgotten, only on known push services. */
class PushIntegrationTest extends TwoAccountsIntegrationTest {

    private static final String[] KEYS;

    static {
        try {
            KEYS = WebPushCrypto.newVapidKeys();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void vapid(DynamicPropertyRegistry registry) {
        registry.add("indezy.push.vapid-public-key", () -> KEYS[0]);
        registry.add("indezy.push.vapid-private-key", () -> KEYS[1]);
    }

    private static Map<String, Object> subscription(String endpoint) {
        return Map.of("endpoint", endpoint, "keys", Map.of("p256dh",
            "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM", "auth", "tBHItJI5svbpez7KI4CCXg"));
    }

    @Test
    void keepsAndForgetsABrowsersSubscription() {
        assertThat(call(alice, HttpMethod.GET, "/push/key", null, Map.class).getBody()).containsEntry("publicKey", KEYS[0]);
        String endpoint = "https://fcm.googleapis.com/fcm/send/alice-" + System.nanoTime();
        assertThat(call(alice, HttpMethod.POST, "/push/subscriptions", subscription(endpoint), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM push_subscriptions WHERE endpoint = ?", Integer.class, endpoint)).isEqualTo(1);
        // The same browser signed in as Bob moves with him; Alice's removal of it no longer touches it.
        call(bob, HttpMethod.POST, "/push/subscriptions", subscription(endpoint), String.class);
        call(alice, HttpMethod.POST, "/push/subscriptions/remove", Map.of("endpoint", endpoint), String.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM push_subscriptions WHERE endpoint = ? AND user_id = ?", Integer.class,
            endpoint, jdbcTemplate.queryForObject("SELECT u.id FROM users u WHERE u.email = ?", Long.class, bobEmail))).isEqualTo(1);
        call(bob, HttpMethod.POST, "/push/subscriptions/remove", Map.of("endpoint", endpoint), String.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM push_subscriptions WHERE endpoint = ?", Integer.class, endpoint)).isZero();

        // Another address than a known push service, or malformed keys, is refused.
        assertThat(call(alice, HttpMethod.POST, "/push/subscriptions", subscription("https://evil.example/x"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/push/subscriptions", Map.of("endpoint", endpoint, "keys", Map.of("p256dh", "<script>",
            "auth", "x")), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void turnsEachPushEventOffOrOnForItsAccount() {
        assertThat(call(alice, HttpMethod.GET, "/push/events", null, Map.class).getBody())
            .isEqualTo(Map.of("NETWORK", true, "MESSAGES", true, "INTERVIEWS", true, "FOLLOW_UPS", true));
        assertThat(call(alice, HttpMethod.PUT, "/push/events", Map.of("MESSAGES", false, "FOLLOW_UPS", false), Map.class).getBody())
            .containsEntry("MESSAGES", false).containsEntry("FOLLOW_UPS", false).containsEntry("NETWORK", true);
        call(alice, HttpMethod.PUT, "/push/events", Map.of("MESSAGES", true), Map.class);
        assertThat(call(alice, HttpMethod.GET, "/push/events", null, Map.class).getBody()).containsEntry("MESSAGES", true)
            .containsEntry("FOLLOW_UPS", false);
        assertThat(call(bob, HttpMethod.GET, "/push/events", null, Map.class).getBody()).containsEntry("FOLLOW_UPS", true);
        assertThat(call(alice, HttpMethod.PUT, "/push/events", Map.of("NOT_AN_EVENT", false), String.class).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
