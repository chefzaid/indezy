package dev.swirlit.indezy.realtime;

import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkPresenceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private final NetworkEventHub hub = mock(NetworkEventHub.class);
    private final NetworkMemberRepository members = mock(NetworkMemberRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final NetworkPresence presence = new NetworkPresence(hub, members, transactions, Clock.fixed(NOW, ZoneOffset.UTC));

    private static NetworkMember member(boolean shows, Instant seen) {
        NetworkMember member = new NetworkMember();
        member.setActive(true);
        member.setPresenceShown(shows ? null : false);
        member.setLastSeenAt(seen);
        return member;
    }

    @Test
    void marksSeenTheMembersWithAStreamOpenHereInATransactionOfItsOwn() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(hub.members()).thenReturn(Set.of(3L, 7L));

        presence.heartbeat();

        verify(members).markSeen(Set.of(3L, 7L), NOW);
        verify(transactions).getTransaction(org.mockito.ArgumentMatchers.argThat((TransactionDefinition definition) ->
            definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void marksNothingWithoutStreamsAndKeepsGoingWhenTheDatabaseFails() {
        when(hub.members()).thenReturn(Set.of());
        presence.heartbeat();
        verify(members, never()).markSeen(anyCollection(), any());

        when(transactions.getTransaction(any())).thenThrow(new IllegalStateException("down"));
        presence.seen(3L);
        verify(members, never()).markSeen(List.of(3L), NOW);
    }

    @Test
    void readsOnlineWhileSeenLatelyAndBothShowIt() {
        NetworkMember reader = member(true, null);
        assertThat(presence.online(reader, member(true, NOW.minusSeconds(30)))).isTrue();
        assertThat(presence.online(reader, member(true, NOW.minus(NetworkPresence.ONLINE_FOR).minusSeconds(1)))).isFalse();
        assertThat(presence.online(reader, member(true, null))).isFalse();
        assertThat(presence.online(reader, member(false, NOW))).isFalse();
        assertThat(presence.online(member(false, null), member(true, NOW))).isFalse();
        NetworkMember away = member(true, NOW);
        away.setActive(false);
        assertThat(presence.online(reader, away)).isFalse();
        assertThat(presence.online(null, member(true, NOW))).isFalse();
        assertThat(presence.online(reader, null)).isFalse();
    }
}
