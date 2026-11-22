package dev.swirlit.indezy.mail;

import java.util.Map;

/**
 * One email to send: its recipient, subject, HTML and plain-text bodies, extra headers (the unsubscribe ones), the key
 * that makes sending it twice send it once, and for an email sent for a member their name before "via Indezy" and the
 * address answers go to (both null for Indezy's own emails).
 */
public record Email(String to, String subject, String html, String text, Map<String, String> headers, String idempotencyKey,
                    String senderName, String replyTo) {

    public Email {
        headers = Map.copyOf(headers);
    }

    /** One of Indezy's own emails. */
    public Email(String to, String subject, String html, String text, Map<String, String> headers, String idempotencyKey) {
        this(to, subject, html, text, headers, idempotencyKey, null, null);
    }

    @Override
    public String toString() {
        // The address and the bodies are personal data, kept out of logs.
        return "Email[" + idempotencyKey + "]";
    }
}
