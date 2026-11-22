package dev.swirlit.indezy.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The AI settings of an account. */
public final class AiDtos {

    private AiDtos() {
    }

    /**
     * Whether AI can be used, through whose key, the model, and with Indezy's key what the account spent this month and
     * may spend, in US dollars; {@code reason} says why not when it cannot ({@code NO_KEY}, {@code ACCOUNT_LIMIT},
     * {@code MONTHLY_LIMIT}).
     */
    public record Status(boolean available, String reason, boolean ownKey, String model, double spentUsd, double limitUsd) {
    }

    /** A member's own Anthropic key and, optionally, the model it should use. */
    public record OwnKey(
        @NotBlank @Size(max = 300) @Pattern(regexp = "sk-ant-[A-Za-z0-9_-]+", message = "An Anthropic key starts with sk-ant-") String apiKey,
        @Pattern(regexp = "claude-(opus|sonnet|haiku)-5-5", message = "Opus, Sonnet or Haiku 5.5") String model) {

        // The key stays out of logs.
        @Override
        public String toString() {
            return "OwnKey[model=" + model + "]";
        }
    }
}
