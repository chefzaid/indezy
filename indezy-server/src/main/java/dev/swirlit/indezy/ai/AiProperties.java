package dev.swirlit.indezy.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The AI features ({@code indezy.ai}, docs/adr/0015-ai-provider.md): Indezy's Anthropic key from Vault and the model it
 * uses, what one account may spend on it a month and what all accounts together may, in US dollars. A member's own key
 * lifts both limits. Without a key, the features say AI is not available.
 */
@ConfigurationProperties(prefix = "indezy.ai")
public record AiProperties(
    String apiKey,
    @DefaultValue("claude-opus-5-5") String model,
    @DefaultValue("https://api.anthropic.com") String baseUrl,
    @DefaultValue("10.0") double accountMonthlyCapUsd,
    @DefaultValue("100.0") double monthlyCapUsd) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // The key stays out of logs.
    @Override
    public String toString() {
        return "AiProperties[configured=" + configured() + ", model=" + model + "]";
    }
}
