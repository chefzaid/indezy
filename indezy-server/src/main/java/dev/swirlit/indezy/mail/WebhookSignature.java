package dev.swirlit.indezy.mail;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Checks a Resend webhook's signature (Resend signs through Svix): the HMAC-SHA256 of {@code <svix-id>.<svix-timestamp>.<body>}
 * with the secret ({@code whsec_} and its key in base64), in base64, among the {@code v1,<signature>} entries of the
 * {@code svix-signature} header, the timestamp within {@value #TOLERANCE_MINUTES} minutes of now against replays.
 */
public final class WebhookSignature {

    static final int TOLERANCE_MINUTES = 5;
    private static final String PREFIX = "whsec_";

    private WebhookSignature() {
    }

    /** Whether the request is Resend's: a valid signature, a timestamp close enough to {@code now}. */
    public static boolean valid(String secret, String id, String timestamp, String signatures, String body, Instant now) {
        if (secret == null || secret.isBlank() || id == null || timestamp == null || signatures == null || body == null) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp.strip());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Duration.between(Instant.ofEpochSecond(seconds), now).abs().compareTo(Duration.ofMinutes(TOLERANCE_MINUTES)) > 0) {
            return false;
        }
        byte[] expected = sign(secret, id.strip() + "." + timestamp.strip() + "." + body);
        if (expected.length == 0) {
            return false;
        }
        for (String entry : signatures.strip().split(" ")) {
            int comma = entry.indexOf(',');
            if (comma > 0 && "v1".equals(entry.substring(0, comma))) {
                byte[] given;
                try {
                    given = Base64.getDecoder().decode(entry.substring(comma + 1));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (MessageDigest.isEqual(expected, given)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The signature of {@code content}, as Svix computes it; empty for a secret that is not one. */
    static byte[] sign(String secret, String content) {
        String key = secret.strip().startsWith(PREFIX) ? secret.strip().substring(PREFIX.length()) : secret.strip();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return new byte[0];
        }
    }
}
