package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.AiUsage;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.AiUsageRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * The AI features' gate (docs/adr/0015-ai-provider.md): a member's own key when they set one, else Indezy's, within what
 * one account may spend on it a month and what all accounts together may; each request's tokens and cost counted by
 * month, feature and key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final double MICROS = 1_000_000.0;
    /** US dollars per million input and output tokens. */
    private static final Map<String, double[]> PRICES = Map.of(
        "claude-opus-5-5", new double[] {4.0, 20.0},
        "claude-sonnet-5-5", new double[] {2.0, 10.0},
        "claude-haiku-5-5", new double[] {0.10, 0.50});

    private final AiProperties properties;
    private final AiClient client;
    private final AiKeyCipher cipher;
    private final AiUsageRepository usage;
    private final UserRepository users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AiDtos.Status status(Long userId) {
        User user = user(userId);
        String month = month();
        boolean own = user.getAiApiKey() != null;
        double spent = usage.spentOnIndezyKey(userId, month) / MICROS;
        String reason = null;
        if (!own) {
            if (!properties.configured()) {
                reason = "NO_KEY";
            } else if (spent >= properties.accountMonthlyCapUsd()) {
                reason = "ACCOUNT_LIMIT";
            } else if (usage.spentOnIndezyKey(month) / MICROS >= properties.monthlyCapUsd()) {
                reason = "MONTHLY_LIMIT";
            }
        }
        Map<AiFeature, String> models = new EnumMap<>(AiFeature.class);
        for (AiFeature feature : AiFeature.values()) {
            models.put(feature, model(user, feature));
        }
        return new AiDtos.Status(reason == null, reason, own, own ? user.getAiModel() : null, models, spent, properties.accountMonthlyCapUsd(),
            own ? taskModels(user.getAiTaskModels()) : Map.of());
    }

    /** Sets the member's own key and model; their requests then go through it, without Indezy's limits. */
    @Transactional
    public AiDtos.Status setOwnKey(Long userId, AiDtos.OwnKey request) {
        User user = user(userId);
        if (user.getAiApiKey() != null) {
            client.forget(cipher.decrypt(user.getAiApiKey()));
        }
        user.setAiApiKey(cipher.encrypt(request.apiKey().strip()));
        user.setAiModel(request.model());
        return status(userId);
    }

    /** Sets the models the member's own key uses for some tasks; a task left out goes back to the key's model or its own. */
    @Transactional
    public AiDtos.Status setTaskModels(Long userId, AiDtos.TaskModels request) {
        User user = user(userId);
        if (user.getAiApiKey() == null) {
            throw new InvalidStateException("Choosing a model per task needs your own Anthropic key");
        }
        user.setAiTaskModels(request.models().isEmpty() ? null : request.models().entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey().name() + "=" + entry.getValue())
            .collect(Collectors.joining(",")));
        return status(userId);
    }

    @Transactional
    public AiDtos.Status removeOwnKey(Long userId) {
        User user = user(userId);
        if (user.getAiApiKey() != null) {
            client.forget(cipher.decrypt(user.getAiApiKey()));
        }
        user.setAiApiKey(null);
        user.setAiModel(null);
        user.setAiTaskModels(null);
        return status(userId);
    }

    /** A structured answer for the member, through their key or Indezy's within its limits, its cost counted. */
    @Transactional
    public <T> T structured(Long userId, AiFeature feature, String system, String text, Class<T> type, long maxTokens,
                            OutputConfig.Effort effort) {
        return call(userId, feature, (key, model) -> client.structured(key, model, system, text, type, maxTokens, effort));
    }

    /** A plain answer to a conversation for the member, as {@link #structured}. */
    @Transactional
    public String chat(Long userId, AiFeature feature, String system, List<AiClient.Turn> turns, long maxTokens, OutputConfig.Effort effort) {
        return call(userId, feature, (key, model) -> client.chat(key, model, system, turns, maxTokens, effort));
    }

    private <T> T call(Long userId, AiFeature feature, BiFunction<String, String, AiClient.Answer<T>> request) {
        User user = user(userId);
        boolean own = user.getAiApiKey() != null;
        if (!own) {
            AiDtos.Status status = status(userId);
            if (!status.available()) {
                throw new InvalidStateException(unavailable(status.reason()));
            }
        }
        String model = model(user, feature);
        AiClient.Answer<T> answer = request.apply(own ? cipher.decrypt(user.getAiApiKey()) : properties.apiKey(), model);
        record(userId, feature, own, model, answer.inputTokens(), answer.outputTokens());
        return answer.value();
    }

    private static String unavailable(String reason) {
        return switch (reason) {
            case "NO_KEY" -> "AI is not available yet";
            case "ACCOUNT_LIMIT" -> "Your AI allowance for this month is used up: add your own key to go on";
            default -> "Indezy's AI allowance for this month is used up: add your own key to go on";
        };
    }

    private void record(Long userId, AiFeature feature, boolean own, String model, long input, long output) {
        double[] price = PRICES.getOrDefault(model, PRICES.get("claude-opus-5-5"));
        long cost = Math.round(input * price[0] + output * price[1]);
        String month = month();
        AiUsage row = usage.findByUserIdAndMonthAndFeatureAndOwnKey(userId, month, feature, own).orElseGet(() -> {
            AiUsage created = new AiUsage();
            created.setUserId(userId);
            created.setMonth(month);
            created.setFeature(feature);
            created.setOwnKey(own);
            return created;
        });
        row.setRequests(row.getRequests() + 1);
        row.setInputTokens(row.getInputTokens() + input);
        row.setOutputTokens(row.getOutputTokens() + output);
        row.setCostMicros(row.getCostMicros() + cost);
        try {
            usage.save(row);
        } catch (DataIntegrityViolationException e) {
            // Two requests of the same month and feature at once: the first one's row counts, this one is logged.
            log.info("AI usage of account {} counted once for two concurrent requests", userId);
        }
    }

    /** The model of {@code feature} for the member: with their key, the one they chose for it, else for every task; else the task's own. */
    private String model(User user, AiFeature feature) {
        if (user.getAiApiKey() == null) {
            return properties.model(feature);
        }
        String chosen = taskModels(user.getAiTaskModels()).get(feature);
        return chosen != null ? chosen : user.getAiModel() != null ? user.getAiModel() : properties.model(feature);
    }

    /** The {@code TASK=model} pairs stored for a member, those still valid. */
    static Map<AiFeature, String> taskModels(String stored) {
        Map<AiFeature, String> models = new EnumMap<>(AiFeature.class);
        if (stored == null || stored.isBlank()) {
            return models;
        }
        for (String pair : stored.split(",")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && PRICES.containsKey(parts[1].strip())) {
                try {
                    models.put(AiFeature.valueOf(parts[0].strip()), parts[1].strip());
                } catch (IllegalArgumentException ignored) {
                    // A task that no longer exists: its choice is dropped.
                }
            }
        }
        return models;
    }

    private String month() {
        return YearMonth.now(clock.withZone(PARIS)).toString();
    }

    private User user(Long userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
