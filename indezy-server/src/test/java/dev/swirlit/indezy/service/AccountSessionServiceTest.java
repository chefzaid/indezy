package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.AccountSession;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.SignInMethod;
import dev.swirlit.indezy.repository.AccountEventRepository;
import dev.swirlit.indezy.repository.AccountSessionRepository;
import dev.swirlit.indezy.util.JwtUtil;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSessionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);

    private final AccountSessionRepository sessions = mock(AccountSessionRepository.class);
    private final AccountEventRepository eventRepository = mock(AccountEventRepository.class);
    private final AccountEventService events = mock(AccountEventService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final AccountSessionService service = new AccountSessionService(sessions, eventRepository, events, jwtUtil,
        Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private AccountSession seen(LocalDateTime lastSeenAt) {
        AccountSession session = new AccountSession();
        session.setId(4L);
        session.setLastSeenAt(lastSeenAt);
        return session;
    }

    @Test
    void notesTheLastActivityOnceEveryFiveMinutesAtMost() {
        when(sessions.findOpen("key", 7L, NOW)).thenReturn(Optional.of(seen(NOW.minusMinutes(2))));
        assertThat(service.authenticate("key", 7L)).contains(4L);
        verify(sessions, never()).touch(anyLong(), any());

        when(sessions.findOpen("key", 7L, NOW)).thenReturn(Optional.of(seen(NOW.minusMinutes(6))));
        assertThat(service.authenticate("key", 7L)).contains(4L);
        verify(sessions).touch(4L, NOW);
    }

    @Test
    void continuesTheOpenSessionOfTheSameAccountOnAKeycloakExchange() {
        User user = new User();
        user.setId(7L);
        user.setEmail("alice@example.com");
        AccountSession open = seen(NOW.minusHours(3));
        open.setSessionKey("key");
        open.setExpiresAt(NOW.plusHours(2));
        when(jwtUtil.lifetime()).thenReturn(Duration.ofHours(24));
        when(jwtUtil.generateToken("alice@example.com", 7L, "key")).thenReturn("same-session");
        when(sessions.findByIdAndUserId(4L, 7L)).thenReturn(Optional.of(open));

        assertThat(service.resumeOrOpen(user, SignInMethod.KEYCLOAK, 4L)).isEqualTo("same-session");
        assertThat(open.getExpiresAt()).isEqualTo(NOW.plusHours(24));
        assertThat(open.getLastSeenAt()).isEqualTo(NOW);
        verify(sessions, never()).save(any());
        verify(events, never()).record(anyLong(), any(), any());

        // Another account's session, an ended one or none: a session of its own.
        open.setRevokedAt(NOW.minusMinutes(1));
        when(jwtUtil.generateToken(eq("alice@example.com"), eq(7L), any())).thenReturn("new-session");
        assertThat(service.resumeOrOpen(user, SignInMethod.KEYCLOAK, 4L)).isEqualTo("new-session");
        assertThat(service.resumeOrOpen(user, SignInMethod.KEYCLOAK, null)).isEqualTo("new-session");
        verify(sessions, times(2)).save(any());
        verify(events, times(2)).record(7L, AccountEventType.SIGNED_IN, "KEYCLOAK");
    }

    @Test
    void refusesATokenWithoutAnOpenSession() {
        assertThat(service.authenticate(null, 7L)).isEmpty();
        assertThat(service.authenticate("key", null)).isEmpty();
        when(sessions.findOpen("key", 7L, NOW)).thenReturn(Optional.empty());
        assertThat(service.authenticate("key", 7L)).isEmpty();
    }

    @Test
    void recordsOtherSessionsEndedOnlyWhenThereWereAny() {
        when(sessions.revokeAll(7L, 4L, NOW)).thenReturn(0);
        assertThat(service.endOthers(7L, 4L)).isZero();
        verify(events, never()).record(anyLong(), any(), any());

        when(sessions.revokeAll(7L, -1L, NOW)).thenReturn(2);
        assertThat(service.endOthers(7L, null)).isEqualTo(2);
        verify(events).record(7L, AccountEventType.OTHER_SESSIONS_REVOKED, "2");
    }

    @Test
    void forgetsSessionsEndedAMonthAgoAndEventsAYearOld() {
        service.forgetOld();
        verify(sessions).deleteEndedBefore(NOW.minusDays(30));
        verify(eventRepository).deleteOlderThan(NOW.minusDays(365));
    }
}
