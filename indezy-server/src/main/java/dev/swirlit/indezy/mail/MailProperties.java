package dev.swirlit.indezy.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
    @DefaultValue("https://api.resend.com") String resendUrl,
    /** The subdomain answers come back to ({@code reply+<token>@replies.swirlit.dev}); empty to send them to the member. */
    @DefaultValue("") String replyDomain,
    /** The signing secret ({@code whsec_…}) of Resend's {@code email.received} webhook. */
    @DefaultValue("") String inboundSecret,
    /** A Resend key allowed to read received emails, when the sending key is restricted to sending; else the sending key. */
    @DefaultValue("") String inboundApiKey) {

    @ConstructorBinding
    public MailProperties {
    }

    /** Without answers coming back through Indezy. */
    public MailProperties(String resendApiKey, String from, String publicUrl, String resendUrl) {
        this(resendApiKey, from, publicUrl, resendUrl, "", "", "");
    }

    /** Whether answers come back through Indezy: a reply domain and the webhook's secret are set, and email can be sent. */
    public boolean replies() {
        return configured() && !replyDomain.isBlank() && !inboundSecret.isBlank();
    }

    /** The key that reads received emails. */
    public String readingKey() {
        return inboundApiKey.isBlank() ? resendApiKey : inboundApiKey;
    }

    /** Whether email can be sent: a Resend key is set. */
    public boolean configured() {
        return resendApiKey != null && !resendApiKey.isBlank();
    }

    /** The app's public address without a trailing slash. */
    public String appUrl() {
        return publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }
}
