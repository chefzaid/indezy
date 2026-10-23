package dev.swirlit.indezy.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How Indezy sends email ({@code indezy.mail}): through Resend, with the key of its account, from a sender of a domain
 * that account verified, with links to the public address of the app. Without a key nothing is sent.
 */
@ConfigurationProperties(prefix = "indezy.mail")
public record MailProperties(
    String resendApiKey,
    @DefaultValue("Indezy <noreply@swirlit.dev>") String from,
    @DefaultValue("https://indezy.swirlit.dev") String publicUrl,
    @DefaultValue("https://api.resend.com") String resendUrl) {

    /** Whether email can be sent: a Resend key is set. */
    public boolean configured() {
        return resendApiKey != null && !resendApiKey.isBlank();
    }

    /** The app's public address without a trailing slash. */
    public String appUrl() {
        return publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }
}
