package dev.swirlit.indezy.repository;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A missing time is never sent to the database as null: PostgreSQL cannot type a null timestamp parameter
 * ("could not determine data type of parameter"), which H2 accepts, so a conversation never read answered 500 there.
 */
class NetworkMessageRepositoryTimesTest {

    private final NetworkMessageRepository messages = mock(NetworkMessageRepository.class, Answers.CALLS_REAL_METHODS);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 17, 0);

    @Test
    void countsFromTheBeginningWhenNothingWasReadYet() {
        messages.unread(3L, 7L, null);
        verify(messages).unreadAfter(3L, 7L, NetworkMessageRepository.BEGINNING);
    }

    @Test
    void keepsAGivenTime() {
        messages.unread(3L, 7L, now);
        verify(messages).unreadAfter(3L, 7L, now);
    }

    @Test
    void emailsFromTheBeginningWhenNothingWasEmailedYet() {
        messages.between(3L, 7L, null, now);
        verify(messages).countBetween(3L, 7L, NetworkMessageRepository.BEGINNING, now);
        messages.latestBetween(3L, 7L, null, now);
        verify(messages).latestSentBetween(3L, 7L, NetworkMessageRepository.BEGINNING, now);
    }
}
