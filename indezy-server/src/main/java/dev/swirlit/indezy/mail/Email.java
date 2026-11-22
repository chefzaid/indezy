package dev.swirlit.indezy.mail;

import java.util.List;
import java.util.Map;

/**
 * One email to send: its recipient, subject, HTML and plain-text bodies, extra headers (the unsubscribe ones), the key
 * that makes sending it twice send it once, and for an email sent for a member their name before "via Indezy" and the
 * address answers go to (both null for Indezy's own emails), and the files attached.
 */
public record Email(String to, String subject, String html, String text, Map<String, String> headers, String idempotencyKey,
                    String senderName, String replyTo, List<Attachment> attachments) {

    /** A file attached to an email. */
    public record Attachment(String fileName, String contentType, byte[] content) {

        public Attachment {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    public Email {
        headers = Map.copyOf(headers);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /** The same email with these files attached. */
    public Email withAttachments(List<Attachment> files) {
        return new Email(to, subject, html, text, headers, idempotencyKey, senderName, replyTo, files);
    }

    /** One of Indezy's own emails. */
    public Email(String to, String subject, String html, String text, Map<String, String> headers, String idempotencyKey) {
        this(to, subject, html, text, headers, idempotencyKey, null, null, List.of());
    }

    @Override
    public String toString() {
        // The address and the bodies are personal data, kept out of logs.
        return "Email[" + idempotencyKey + "]";
    }
}
