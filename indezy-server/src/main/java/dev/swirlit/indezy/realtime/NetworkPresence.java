package dev.swirlit.indezy.realtime;

import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Who is online in the member network (see docs/features.md#member-network): a member with the app open holds a live
 * stream on some replica, which marks them seen as it opens and at each heartbeat, so a member reads as online while
 * seen in the last {@link #ONLINE_FOR}, whichever replica holds their stream. It shows only between two members who
 * both show it.
 */
@Component
@Slf4j
public class NetworkPresence {

    /** Two heartbeats and a margin: a member whose app closed reads as offline within this time. */
    public static final Duration ONLINE_FOR = Duration.ofSeconds(70);

    private final NetworkEventHub hub;
    private final NetworkMemberRepository members;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public NetworkPresence(NetworkEventHub hub, NetworkMemberRepository members, PlatformTransactionManager transactionManager, Clock clock) {
        this.hub = hub;
        this.members = members;
        // Its own transaction: the stream opens within a read-only one, which a write would mark to roll back.
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Marks the member seen now: they just opened the app. */
    public void seen(Long userId) {
        mark(List.of(userId));
    }

    /** Marks seen the members with a stream open on this replica, as the heartbeat keeps them open. */
    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT25S")
    public void heartbeat() {
        mark(hub.members());
    }

    /** Whether the reader sees the other member online: both show it, and the other was seen lately. */
    public boolean online(NetworkMember reader, NetworkMember other) {
        return reader != null && other != null && reader.showsPresence() && other.showsPresence() && other.isActive() &&
            other.getLastSeenAt() != null && other.getLastSeenAt().isAfter(clock.instant().minus(ONLINE_FOR));
    }

    private void mark(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        try {
            transactions.executeWithoutResult(status -> members.markSeen(userIds, clock.instant()));
        } catch (RuntimeException e) {
            // Being seen is best effort: the next heartbeat marks the member again.
            log.warn("Members could not be marked seen: {}", e.getClass().getSimpleName());
        }
    }
}
