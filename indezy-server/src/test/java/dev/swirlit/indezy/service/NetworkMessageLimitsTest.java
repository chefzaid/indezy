package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.repository.NetworkConversationRepository;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkMessageLimitsTest {

    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final NetworkLimits limits = mock(NetworkLimits.class);
    private final NetworkMessageRepository messages = mock(NetworkMessageRepository.class);
    private final NetworkConversationRepository conversations = mock(NetworkConversationRepository.class);
    private final NetworkMessageLimits messageLimits = new NetworkMessageLimits(limits, messages, conversations, clock);

    @Test
    void refusesBeyondTheHourlyMessagesAndTheDailyConversations() {
        when(messages.countBySenderIdAndCreatedAtAfter(eq(1L), any())).thenReturn((long) NetworkMessageLimits.MESSAGES_PER_HOUR - 1,
            (long) NetworkMessageLimits.MESSAGES_PER_HOUR);
        assertThatCode(() -> messageLimits.message(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> messageLimits.message(1L)).isInstanceOf(TooManyRequestsException.class).hasMessageContaining("messages");
        verify(messages, org.mockito.Mockito.atLeastOnce())
            .countBySenderIdAndCreatedAtAfter(1L, LocalDateTime.ofInstant(now, ZoneId.systemDefault()).minusHours(1));

        when(conversations.countByCreatedByAndCreatedAtAfter(eq(1L), any())).thenReturn((long) NetworkMessageLimits.CONVERSATIONS_PER_DAY);
        assertThatThrownBy(() -> messageLimits.conversation(1L)).isInstanceOf(TooManyRequestsException.class)
            .hasMessageContaining("conversations");
        verify(conversations).countByCreatedByAndCreatedAtAfter(1L, LocalDateTime.ofInstant(now, ZoneId.systemDefault()).minusDays(1));
    }

    @Test
    void refusesASuspendedMember() {
        doThrow(new InvalidStateException("The moderators suspended you")).when(limits).notSuspended(2L);
        assertThatThrownBy(() -> messageLimits.message(2L)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> messageLimits.conversation(2L)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void keepsTheEditWindowAndSaysWhyAMessageCannotGo() {
        NetworkConversations shared = new NetworkConversations(null, null, null, null, null, null, clock);
        NetworkMessage message = new NetworkMessage();
        message.setCreatedAt(LocalDateTime.ofInstant(now, ZoneId.systemDefault()).minusMinutes(14));
        assertThat(shared.editable(message)).isTrue();
        message.setCreatedAt(LocalDateTime.ofInstant(now, ZoneId.systemDefault()).minusMinutes(16));
        assertThat(shared.editable(message)).isFalse();
        message.setCreatedAt(LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
        message.setDeletedAt(now);
        assertThat(shared.editable(message)).isFalse();
        assertThat(NetworkConversations.excerpt("x".repeat(100))).hasSize(NetworkConversations.PREVIEW).endsWith("…");
        assertThat(NetworkConversations.excerpt(" Bonjour\n\nBob ")).isEqualTo("Bonjour Bob");
        assertThat(NetworkConversationService.reason(NetworkMessageDtos.Closed.BLOCKED)).contains("blocks");
        assertThat(NetworkConversationService.reason(NetworkMessageDtos.Closed.LEFT)).contains("left");
    }
}
