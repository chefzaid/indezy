package dev.swirlit.indezy.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

/**
 * Tells members' open pages what changed (see docs/features.md#member-network), once the change is committed: a
 * transaction rolled back tells nothing.
 */
@Component
@RequiredArgsConstructor
public class NetworkEvents {

    private final NetworkEventRelay relay;

    /** A conversation's messages changed: sent, edited, deleted, reacted to, or the group renamed or joined. */
    public void message(Collection<Long> participants, Long conversationId) {
        afterCommit(List.copyOf(participants), new NetworkEvent(NetworkEvent.MESSAGE, conversationId, null));
    }

    /** One of a conversation's members read it. */
    public void read(Collection<Long> participants, Long conversationId) {
        afterCommit(List.copyOf(participants), new NetworkEvent(NetworkEvent.READ, conversationId, null));
    }

    /** A member is writing in the conversation. */
    public void typing(Collection<Long> to, Long conversationId, String firstName) {
        afterCommit(List.copyOf(to), new NetworkEvent(NetworkEvent.TYPING, conversationId, firstName));
    }

    /** A notification came. */
    public void notice(Long recipientId) {
        afterCommit(List.of(recipientId), new NetworkEvent(NetworkEvent.NOTICE, null, null));
    }

    private void afterCommit(List<Long> to, NetworkEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    relay.send(to, event);
                }
            });
        } else {
            relay.send(to, event);
        }
    }
}
