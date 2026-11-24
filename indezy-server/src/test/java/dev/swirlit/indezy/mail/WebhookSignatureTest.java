package dev.swirlit.indezy.mail;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString("a webhook signing key".getBytes(StandardCharsets.UTF_8));

    /** The {@code svix-signature} header Resend would send for this body. */
    static String header(String id, long timestamp, String body) {
        return "v1," + Base64.getEncoder().encodeToString(WebhookSignature.sign(SECRET, id + "." + timestamp + "." + body));
    }

    @Test
    void acceptsResendsSignatureAndRefusesAnyChangeOrAnOldOne() {
        Instant now = Instant.parse("2026-11-24T10:00:00Z");
        long at = now.getEpochSecond();
        String body = "{\"type\":\"email.received\"}";
        String signature = header("msg_1", at, body);

        assertThat(WebhookSignature.valid(SECRET, "msg_1", Long.toString(at), signature, body, now)).isTrue();
        // Among several signatures, one valid is enough (the key being rotated).
        assertThat(WebhookSignature.valid(SECRET, "msg_1", Long.toString(at), "v1,AAAA " + signature, body, now)).isTrue();
        assertThat(WebhookSignature.valid(SECRET, "msg_1", Long.toString(at), signature, body + " ", now)).isFalse();
        assertThat(WebhookSignature.valid(SECRET, "msg_2", Long.toString(at), signature, body, now)).isFalse();
        assertThat(WebhookSignature.valid(SECRET, "msg_1", Long.toString(at), signature, body, now.plusSeconds(6 * 60))).isFalse();
        assertThat(WebhookSignature.valid(SECRET, "msg_1", "soon", signature, body, now)).isFalse();
        assertThat(WebhookSignature.valid("", "msg_1", Long.toString(at), signature, body, now)).isFalse();
        assertThat(WebhookSignature.valid("whsec_not base64!", "msg_1", Long.toString(at), signature, body, now)).isFalse();
    }
}
