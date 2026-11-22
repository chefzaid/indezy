package dev.swirlit.indezy.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredOutputConfig;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls Claude through the official SDK with a structured answer: the system prompt, the member's text, the class the
 * answer fills. The server-side refusal fallback is on; a refusal still standing is told as such, and a failed call as
 * the service being unavailable. One client per key is kept (Indezy's and each member's own).
 */
@Slf4j
@Component
public class AiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private final AiProperties properties;
    private final Map<String, AnthropicClient> clients = new ConcurrentHashMap<>();

    public AiClient(AiProperties properties) {
        this.properties = properties;
    }

    /** A structured answer and the tokens it used. */
    public record Answer<T>(T value, long inputTokens, long outputTokens) {
    }

    public <T> Answer<T> structured(String apiKey, String model, String system, String text, Class<T> type, long maxTokens,
                                    OutputConfig.Effort effort) {
        AnthropicClient client = clients.computeIfAbsent(apiKey, key -> AnthropicOkHttpClient.builder().apiKey(key)
            .baseUrl(properties.baseUrl()).timeout(TIMEOUT).build());
        try {
            StructuredMessage<T> message = client.messages().create(MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .addUserMessage(text)
                .outputConfig(StructuredOutputConfig.<T>builder().effort(effort).format(type).build())
                // A refusal by the safety classifiers is retried on the model they choose.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build());
            if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                throw new InvalidStateException("The AI declined this request");
            }
            if (message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
                throw new InvalidStateException("The AI answer was cut short: shorten the text");
            }
            T value = message.content().stream().flatMap(block -> block.text().stream()).findFirst()
                .orElseThrow(() -> new ServiceUnavailableException("The AI gave no answer", null)).text();
            return new Answer<>(value, message.usage().inputTokens(), message.usage().outputTokens());
        } catch (AnthropicServiceException e) {
            log.warn("AI request failed with status {}", e.statusCode());
            if (e.statusCode() == UNAUTHORIZED || e.statusCode() == FORBIDDEN) {
                throw new InvalidStateException("The AI key was refused: check it");
            }
            throw new ServiceUnavailableException("The AI service is unavailable for now", e);
        } catch (AnthropicException e) {
            log.warn("AI request failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("The AI service is unavailable for now", e);
        }
    }

    /** Forgets the client of a key the member removed. */
    public void forget(String apiKey) {
        AnthropicClient client = clients.remove(apiKey);
        if (client != null) {
            client.close();
        }
    }
}
