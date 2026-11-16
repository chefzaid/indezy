package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.model.enums.NetworkReaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Conversations between members and their messages (see docs/features.md#member-network). */
public final class NetworkMessageDtos {

    private NetworkMessageDtos() {
    }

    /** Why a message cannot be sent; none while it can. */
    public enum Closed {
        /** The other member does not follow the sender. */
        NOT_FOLLOWED,
        /** One of the two blocks the other. */
        BLOCKED,
        /** The other member left the network. */
        LEFT
    }

    /** A conversation in the list: the other member, its latest message, what is unread and whether a message can go. */
    public record Conversation(Long id, NetworkDtos.MemberCard with, Preview last, int unread, boolean muted, boolean archived,
                               Closed closed, LocalDateTime lastMessageAt) {
    }

    /** The latest message's first words. */
    public record Preview(boolean mine, String text, boolean deleted, LocalDateTime sentAt) {

        @Override
        public String toString() {
            return "Preview[mine=" + mine + "]";
        }
    }

    /** A message as its reader reads it. */
    public record Message(Long id, boolean mine, String text, LocalDateTime sentAt, boolean edited, boolean deleted, boolean editable,
                          Reply replyTo, List<NetworkDtos.ReactionCount> reactions, NetworkReaction myReaction, Card card) {

        public Message {
            reactions = List.copyOf(reactions);
        }

        // Spring MVC logs bodies through toString at DEBUG: not what members wrote each other.
        @Override
        public String toString() {
            return "Message[id=" + id + "]";
        }
    }

    /**
     * An opportunity shared as a card: its facts, and, for its recipient, the opportunity it became in their pipeline once
     * added (none before).
     */
    public record Card(String role, Integer dailyRate, String city, LocalDate startDate, Integer remoteDaysPerMonth, List<String> stack,
                       Long takenProjectId) {

        public Card {
            stack = List.copyOf(stack);
        }
    }

    /** The message a message replies to, quoted. */
    public record Reply(Long id, boolean mine, String excerpt, boolean deleted) {

        @Override
        public String toString() {
            return "Reply[id=" + id + "]";
        }
    }

    /** A conversation with a page of its messages, the oldest first, and whether older ones remain. */
    public record Thread(Conversation conversation, List<Message> messages, boolean more) {

        public Thread {
            messages = List.copyOf(messages);
        }
    }

    /** Opens the conversation with a member, or finds the one open. */
    public record Open(@NotBlank @Size(max = 60) String handle) {
    }

    /** A message: its text, which may stay empty with an opportunity shared, the message it replies to, the opportunity. */
    public record NewMessage(@Size(max = NetworkMessage.TEXT_MAX) String text, Long replyTo, Long opportunityId) {

        @Override
        public String toString() {
            return "NewMessage[replyTo=" + replyTo + ", opportunityId=" + opportunityId + "]";
        }
    }

    public record EditedMessage(@NotBlank @Size(max = NetworkMessage.TEXT_MAX) String text) {

        @Override
        public String toString() {
            return "EditedMessage";
        }
    }

    /** Mutes or archives a conversation, for its reader alone. */
    public record Settings(Boolean muted, Boolean archived) {
    }

    /** A message found by a search, with its conversation and the other member. */
    public record Found(Long conversationId, NetworkDtos.MemberCard with, Long messageId, boolean mine, String excerpt, LocalDateTime sentAt) {

        @Override
        public String toString() {
            return "Found[messageId=" + messageId + "]";
        }
    }

    /** How many conversations have unread messages, the muted aside. */
    public record Unread(int conversations) {
    }

    /** A message the account sent, for its data export. */
    public record Exported(Long conversationId, String to, String text, LocalDateTime sentAt, boolean deleted) {

        @Override
        public String toString() {
            return "Exported[conversationId=" + conversationId + "]";
        }
    }
}
