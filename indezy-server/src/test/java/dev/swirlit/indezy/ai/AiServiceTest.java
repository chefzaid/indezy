package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.AiUsageRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiServiceTest {

    private final AiUsageRepository usage = mock(AiUsageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AiClient client = mock(AiClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);

    private AiService service(String key) {
        User user = new User();
        user.setId(7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        return new AiService(new AiProperties(key, "claude-opus-5-5", "https://api.anthropic.com", 2.0, 100.0), client,
            new AiKeyCipher("secret"), usage, users, clock);
    }

    @Test
    void saysAiIsNotAvailableWithoutAKey() {
        AiService service = service(null);
        assertThat(service.status(7L).available()).isFalse();
        assertThat(service.status(7L).reason()).isEqualTo("NO_KEY");
        assertThatThrownBy(() -> service.structured(7L, AiFeature.POSTING, "s", "t", String.class, 10, OutputConfig.Effort.LOW))
            .isInstanceOf(InvalidStateException.class).hasMessage("AI is not available yet");
        verifyNoInteractions(client);
    }

    @Test
    void stopsEveryoneOnceIndezysMonthlyCapIsReached() {
        AiService service = service("sk-ant-indezy");
        when(usage.spentOnIndezyKey(7L, "2026-11")).thenReturn(10_000L);
        when(usage.spentOnIndezyKey(anyString())).thenReturn(100_000_000L);
        assertThat(service.status(7L).reason()).isEqualTo("MONTHLY_LIMIT");
        assertThat(service.status(7L).spentUsd()).isEqualTo(0.01);
        assertThatThrownBy(() -> service.structured(7L, AiFeature.MESSAGE, "s", "t", String.class, 10, OutputConfig.Effort.LOW))
            .hasMessage("Indezy's AI allowance for this month is used up: add your own key to go on");
    }
}
