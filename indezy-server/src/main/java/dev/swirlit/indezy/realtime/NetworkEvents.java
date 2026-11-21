package dev.swirlit.indezy.realtime;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.push.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Tells members' open pages what changed (see docs/features.md#member-network), once the change is committed: a
 * transaction rolled back tells nothing. Notifications and new messages also reach the browsers that accepted web push.
 */
@Component
@RequiredArgsConstructor
public class NetworkEvents {

    private final NetworkEventRelay relay;
    private final PushService push;

    private static final Map<DocumentLanguage, String[]> NOTICE = Map.of(
        DocumentLanguage.EN, new String[] {"Indezy", "You have a new notification on the network."},
        DocumentLanguage.FR, new String[] {"Indezy", "Vous avez une nouvelle notification sur le réseau."});
    private static final Map<DocumentLanguage, String[]> MESSAGE = Map.of(
        DocumentLanguage.EN, new String[] {"New message", "A member wrote to you on Indezy."},
        DocumentLanguage.FR, new String[] {"Nouveau message", "Un membre vous a écrit sur Indezy."});

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
        afterCommit(() -> push.notify(List.of(recipientId), NOTICE, "/network/notifications"));
    }

    /** A message came for these members, who did not mute its conversation: pushed to their browsers. */
    public void messaged(Collection<Long> recipients, Long conversationId) {
        List<Long> to = List.copyOf(recipients);
        afterCommit(() -> push.notify(to, MESSAGE, "/network/messages/" + conversationId));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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
