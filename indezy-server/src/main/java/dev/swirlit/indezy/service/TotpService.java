package dev.swirlit.indezy.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * RFC 6238 time-based one-time passwords (TOTP), compatible with authenticator apps
 * such as Google Authenticator (HMAC-SHA1, 6 digits, 30-second period).
 */
@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    /** Accept codes from the adjacent time steps to tolerate clock drift. */
    private static final int WINDOW = 1;

    private final SecureRandom secureRandom = new SecureRandom();

    /** Generates a new random Base32-encoded shared secret. */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** Builds the {@code otpauth://} provisioning URI an authenticator app can consume. */
    public String buildProvisioningUri(String secret, String account, String issuer) {
        String encodedIssuer = urlEncode(issuer);
        return "otpauth://totp/" + encodedIssuer + ":" + urlEncode(account) +
            "?secret=" + secret +
            "&issuer=" + encodedIssuer +
            "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    /** Returns the TOTP valid right now for the given Base32 secret. */
    public String generateCurrentCode(String secret) {
        long counter = (System.currentTimeMillis() / 1000L) / PERIOD_SECONDS;
        return generateCode(base32Decode(secret), counter);
    }

    /** True when {@code code} matches the current TOTP for {@code secret} (± one time step). */
    public boolean validateCode(String secret, String code) {
        if (secret == null || code == null || code.length() != DIGITS) {
            return false;
        }
        byte[] key = base32Decode(secret);
        long counter = (System.currentTimeMillis() / 1000L) / PERIOD_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (generateCode(key, counter + offset).equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** Computes the {@value #DIGITS}-digit TOTP for a raw key and time-step counter. */
    String generateCode(byte[] key, long counter) {
        byte[] counterBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xff);
            counter >>= 8;
        }
        byte[] hash = hmacSha1(key, counterBytes);
        int start = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[start] & 0x7f) << 24) |
            ((hash[start + 1] & 0xff) << 16) |
            ((hash[start + 2] & 0xff) << 8) |
            (hash[start + 3] & 0xff);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    private byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute TOTP", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                result.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1f));
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return result.toString();
    }

    private byte[] base32Decode(String encoded) {
        String cleaned = encoded.trim().replace("=", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        int outputLength = cleaned.length() * 5 / 8;
        byte[] result = new byte[outputLength];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : cleaned.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                continue;
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                result[index++] = (byte) ((buffer >> bitsLeft) & 0xff);
            }
        }
        return result;
    }
}
