package dev.swirlit.indezy.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The AI features ({@code indezy.ai}, docs/adr/0015-ai-provider.md): Indezy's Anthropic key from Vault and the models it
 * uses, what one account may spend on it a month and what all accounts together may, in US dollars. A member's own key
 * lifts both limits. Without a key, the features say AI is not available.
 *
 * <p>Each task has its model ({@link #BY_TASK}): Haiku reads postings and triages offers, Sonnet writes messages,
 * tailors dossiers and suggests rates, Opus weighs opportunities, holds conversations and
 * prepares negotiations and interviews. {@code models} changes one task's, {@code model}
 * puts every task without one in {@code models} on a single model.
 */
@ConfigurationProperties(prefix = "indezy.ai")
public record AiProperties(
    String apiKey,
    String model,
    @DefaultValue("https://api.anthropic.com") String baseUrl,
    @DefaultValue("10.0") double accountMonthlyCapUsd,
    @DefaultValue("100.0") double monthlyCapUsd,
    Map<String, String> models) {

    public static final String OPUS = "claude-opus-5-5";
    public static final String SONNET = "claude-sonnet-5-5";
    public static final String HAIKU = "claude-haiku-5-5";

    /** The model each task uses unless configured otherwise: the cheapest that does it well. */
    public static final Map<AiFeature, String> BY_TASK = Map.of(AiFeature.POSTING, HAIKU, AiFeature.TRIAGE, HAIKU,
        AiFeature.MESSAGE, SONNET, AiFeature.DOSSIER, SONNET, AiFeature.RATE, SONNET, AiFeature.FIT, OPUS, AiFeature.CHAT, OPUS,
        AiFeature.NEGOTIATION, OPUS, AiFeature.INTERVIEW, OPUS);

    @ConstructorBinding
    public AiProperties {
        // Keys as AiFeature names whatever their case: INDEZY_AI_MODELS_CHAT binds as "chat".
        Map<String, String> byName = new HashMap<>();
        (models == null ? Map.<String, String>of() : models).forEach((task, name) -> byName.put(task.strip().toUpperCase(Locale.ROOT), name));
        models = Map.copyOf(byName);
    }

    /** The properties without models of their own: every task on its {@link #BY_TASK} model unless {@code model} is set. */
    public AiProperties(String apiKey, String model, String baseUrl, double accountMonthlyCapUsd, double monthlyCapUsd) {
        this(apiKey, model, baseUrl, accountMonthlyCapUsd, monthlyCapUsd, null);
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** The model {@code feature} uses on Indezy's key. */
    public String model(AiFeature feature) {
        String own = models.get(feature.name());
        if (own != null && !own.isBlank()) {
            return own.strip();
        }
        return model != null && !model.isBlank() ? model.strip() : BY_TASK.getOrDefault(feature, OPUS);
    }

    // The key stays out of logs.
    @Override
    public String toString() {
        return "AiProperties[configured=" + configured() + ", model=" + model + ", models=" + models + "]";
    }
}
