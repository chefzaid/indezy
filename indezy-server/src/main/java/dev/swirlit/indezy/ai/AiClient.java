package dev.swirlit.indezy.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.TextBlock;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** One turn of a conversation: the member's or the AI's. */
    public record Turn(boolean fromMember, String text) {
    }

    /** A plain answer to a conversation, its turns oldest first, the last one the member's. */
    public Answer<String> chat(String apiKey, String model, String system, List<Turn> turns, long maxTokens, OutputConfig.Effort effort) {
        MessageCreateParams.Builder params = MessageCreateParams.builder().model(model).maxTokens(maxTokens).system(system)
            .outputConfig(OutputConfig.builder().effort(effort).build())
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        turns.forEach(turn -> {
            if (turn.fromMember()) {
                params.addUserMessage(turn.text());
            } else {
                params.addAssistantMessage(turn.text());
            }
        });
        try {
            Message message = client(apiKey).messages().create(params.build());
            stopped(message.stopReason());
            String text = message.content().stream().flatMap(block -> block.text().stream()).map(TextBlock::text)
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n\n" + right).strip();
            if (text.isEmpty()) {
                throw new ServiceUnavailableException("The AI gave no answer", null);
            }
            return new Answer<>(text, message.usage().inputTokens(), message.usage().outputTokens());
        } catch (AnthropicServiceException e) {
            if (keyRefused(e)) {
                throw new InvalidStateException("The AI key was refused: check it");
            }
            throw new ServiceUnavailableException("The AI service is unavailable for now", e);
        } catch (AnthropicException e) {
            log.warn("AI request failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("The AI service is unavailable for now", e);
        }
    }

    public <T> Answer<T> structured(String apiKey, String model, String system, String text, Class<T> type, long maxTokens,
                                    OutputConfig.Effort effort) {
        AnthropicClient client = client(apiKey);
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
            stopped(message.stopReason());
            T value = message.content().stream().flatMap(block -> block.text().stream()).findFirst()
                .orElseThrow(() -> new ServiceUnavailableException("The AI gave no answer", null)).text();
            return new Answer<>(value, message.usage().inputTokens(), message.usage().outputTokens());
        } catch (AnthropicServiceException e) {
            if (keyRefused(e)) {
                throw new InvalidStateException("The AI key was refused: check it");
            }
            throw new ServiceUnavailableException("The AI service is unavailable for now", e);
        } catch (AnthropicException e) {
            log.warn("AI request failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("The AI service is unavailable for now", e);
        }
    }

    private AnthropicClient client(String apiKey) {
        return clients.computeIfAbsent(apiKey, key -> AnthropicOkHttpClient.builder().apiKey(key).baseUrl(properties.baseUrl())
            .timeout(TIMEOUT).build());
    }

    /** A refusal still standing, or an answer cut short, said as such. */
    private static void stopped(Optional<StopReason> reason) {
        if (reason.filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new InvalidStateException("The AI declined this request");
        }
        if (reason.filter(StopReason.MAX_TOKENS::equals).isPresent()) {
            throw new InvalidStateException("The AI answer was cut short: shorten the text");
        }
    }

    /** Whether Anthropic refused the key, logging the failure either way. */
    private static boolean keyRefused(AnthropicServiceException e) {
        log.warn("AI request failed with status {}", e.statusCode());
        return e.statusCode() == UNAUTHORIZED || e.statusCode() == FORBIDDEN;
    }

    /** Forgets the client of a key the member removed. */
    public void forget(String apiKey) {
        AnthropicClient client = clients.remove(apiKey);
        if (client != null) {
            client.close();
        }
    }
}
