package dev.swirlit.indezy.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web push ({@code indezy.push}): the VAPID key pair that signs Indezy's pushes, as base64url (the public key as the
 * 65-byte uncompressed point, the private key as its 32-byte scalar), from Vault (see docs/deployment.md#web-push-keys).
 * Without both, nothing is pushed and the app says push is not available.
 */
@ConfigurationProperties(prefix = "indezy.push")
public record PushProperties(String vapidPublicKey, String vapidPrivateKey) {

    public boolean configured() {
        return vapidPublicKey != null && !vapidPublicKey.isBlank() && vapidPrivateKey != null && !vapidPrivateKey.isBlank();
    }

    // The private key stays out of logs.
    @Override
    public String toString() {
        return "PushProperties[configured=" + configured() + "]";
    }
}
