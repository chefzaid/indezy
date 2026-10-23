package dev.swirlit.indezy.mail;

import java.util.Map;

/**
 * One email to send: its recipient, subject, HTML and plain-text bodies, extra headers (the unsubscribe ones) and the
 * key that makes sending it twice send it once.
 */
public record Email(String to, String subject, String html, String text, Map<String, String> headers, String idempotencyKey) {

    public Email {
        headers = Map.copyOf(headers);
    }

    @Override
    public String toString() {
        // The address and the bodies are personal data, kept out of logs.
        return "Email[" + idempotencyKey + "]";
    }
}
