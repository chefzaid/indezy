package dev.swirlit.indezy.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Secret tokens (links, unsubscribe and API tokens) and the SHA-256 hashes kept in their place. */
public final class Tokens {

    private static final int RANDOM_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    /** A new secret: 32 random bytes, URL-safe Base64 without padding (43 characters). */
    public static String random() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The SHA-256 of the text's UTF-8 bytes, in lower-case hex (64 characters). */
    public static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
