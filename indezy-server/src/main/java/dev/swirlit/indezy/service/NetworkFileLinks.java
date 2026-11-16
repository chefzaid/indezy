package dev.swirlit.indezy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * The signed addresses of the network's files (see docs/security.md#member-network): handed only to a reader who may
 * see the post or the message, each valid one to two hours (to the end of the next hour, so that a page read again
 * within the hour gets the same address and the browser's copy); signed with a key derived from the token secret.
 */
@Component
public final class NetworkFileLinks {

    public static final String FULL = "full";
    public static final String PREVIEW = "preview";
    private static final long HOUR = 3600;

    private final SecretKeySpec key;

    public NetworkFileLinks(@Value("${jwt.secret}") String secret) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256").digest(("network-files|" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "HmacSHA256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }

    /** The address of the file, or of its first page's picture, relative to the API. */
    public String url(Long fileId, String variant, Instant now) {
        long expires = (now.getEpochSecond() / HOUR + 2) * HOUR;
        return "/public/network-files/" + fileId + "/" + variant + "?e=" + expires + "&s=" + sign(fileId, variant, expires);
    }

    /** Whether the address was signed here and has not expired. */
    public boolean valid(Long fileId, String variant, long expires, String signature, Instant now) {
        return signature != null && expires > now.getEpochSecond() &&
            MessageDigest.isEqual(sign(fileId, variant, expires).getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(Long fileId, String variant, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] signed = mac.doFinal((fileId + ":" + variant + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is missing", e);
        }
    }
}
