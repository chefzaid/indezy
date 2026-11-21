package dev.swirlit.indezy.push;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushCryptoTest {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    @Test
    void encryptsAMessageOnlyTheBrowserReads() throws Exception {
        KeyPair browser = WebPushCrypto.generate();
        byte[] browserKey = WebPushCrypto.point((ECPublicKey) browser.getPublic());
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        byte[] plaintext = "{\"notification\":{\"title\":\"Indezy\"}}".getBytes(StandardCharsets.UTF_8);

        byte[] message = WebPushCrypto.encrypt(plaintext, URL.encodeToString(browserKey), URL.encodeToString(secret));

        // The aes128gcm header: a 16-byte salt, the record size, then the sender's 65-byte key.
        ByteBuffer header = ByteBuffer.wrap(message);
        header.position(16);
        assertThat(header.getInt()).isEqualTo(WebPushCrypto.RECORD_SIZE);
        assertThat(header.get()).isEqualTo((byte) 65);
        assertThat(message.length).isEqualTo(16 + 4 + 1 + 65 + plaintext.length + 1 + 16);
        assertThat(WebPushCrypto.decrypt(message, (ECPrivateKey) browser.getPrivate(), browserKey, secret)).isEqualTo(plaintext);
        // Each message has its own salt and sender key.
        assertThat(WebPushCrypto.encrypt(plaintext, URL.encodeToString(browserKey), URL.encodeToString(secret))).isNotEqualTo(message);
    }

    @Test
    void signsTheVapidTokenWithThePrivateKey() throws Exception {
        String[] keys = WebPushCrypto.newVapidKeys();
        assertThat(Base64.getUrlDecoder().decode(keys[0])).hasSize(65);
        assertThat(Base64.getUrlDecoder().decode(keys[1])).hasSize(32);

        String token = WebPushCrypto.vapidToken("https://fcm.googleapis.com", "https://indezy.swirlit.dev", 1_800_000_000L, keys[1], keys[0]);

        String[] parts = token.split("\\.");
        assertThat(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
            .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1800000000,\"sub\":\"https://indezy.swirlit.dev\"}");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPushCrypto.publicKey(Base64.getUrlDecoder().decode(keys[0])));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }
}
