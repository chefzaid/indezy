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

    @Test
    void putsEachTaskOnItsModelUnlessOneIsChosen() {
        AiProperties byTask = new AiProperties("sk-ant-indezy", "", "https://api.anthropic.com", 2.0, 100.0);
        assertThat(byTask.model(AiFeature.POSTING)).isEqualTo("claude-haiku-5-5");
        assertThat(byTask.model(AiFeature.TRIAGE)).isEqualTo("claude-haiku-5-5");
        assertThat(byTask.model(AiFeature.MESSAGE)).isEqualTo("claude-sonnet-5-5");
        assertThat(byTask.model(AiFeature.DOSSIER)).isEqualTo("claude-sonnet-5-5");
        assertThat(byTask.model(AiFeature.RATE)).isEqualTo("claude-sonnet-5-5");
        assertThat(byTask.model(AiFeature.FIT)).isEqualTo("claude-opus-5-5");
        assertThat(byTask.model(AiFeature.CHAT)).isEqualTo("claude-opus-5-5");
        assertThat(byTask.model(AiFeature.NEGOTIATION)).isEqualTo("claude-opus-5-5");
        assertThat(byTask.model(AiFeature.INTERVIEW)).isEqualTo("claude-opus-5-5");
        assertThat(AiProperties.BY_TASK).containsOnlyKeys(AiFeature.values());

        AiProperties one = new AiProperties("sk-ant-indezy", "claude-sonnet-5-5", "https://api.anthropic.com", 2.0, 100.0,
            java.util.Map.of("chat", " claude-haiku-5-5 "));
        assertThat(one.model(AiFeature.POSTING)).isEqualTo("claude-sonnet-5-5");
        assertThat(one.model(AiFeature.CHAT)).isEqualTo("claude-haiku-5-5");
        assertThat(one.toString()).doesNotContain("sk-ant-indezy");
    }

    @Test
    void tellsTheModelOfEachTaskAndTheOneChosenWithTheMembersKey() {
        User user = new User();
        user.setId(8L);
        when(users.findById(8L)).thenReturn(Optional.of(user));
        AiService service = new AiService(new AiProperties("sk-ant-indezy", null, "https://api.anthropic.com", 2.0, 100.0), client,
            new AiKeyCipher("secret"), usage, users, clock);
        AiDtos.Status byTask = service.status(8L);
        assertThat(byTask.model()).isNull();
        assertThat(byTask.models()).containsEntry(AiFeature.POSTING, "claude-haiku-5-5").containsEntry(AiFeature.FIT, "claude-opus-5-5");

        service.setOwnKey(8L, new AiDtos.OwnKey("sk-ant-own", null));
        assertThat(service.status(8L).model()).isNull();
        assertThat(service.status(8L).models()).containsEntry(AiFeature.MESSAGE, "claude-sonnet-5-5");
        service.setOwnKey(8L, new AiDtos.OwnKey("sk-ant-own", "claude-haiku-5-5"));
        assertThat(service.status(8L).model()).isEqualTo("claude-haiku-5-5");
        assertThat(service.status(8L).models()).containsEntry(AiFeature.FIT, "claude-haiku-5-5");
    }

    @Test
    void readsTheModelsChosenPerTaskKeepingTheValidOnes() {
        assertThat(AiService.taskModels(null)).isEmpty();
        assertThat(AiService.taskModels("MESSAGE=claude-opus-5-5,GONE=claude-haiku-5-5,FIT=gpt-5,POSTING=claude-sonnet-5-5"))
            .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(AiFeature.MESSAGE, "claude-opus-5-5", AiFeature.POSTING, "claude-sonnet-5-5"));
    }
}
