package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A message in a conversation: its text, the message it replies to, and whether its sender edited or deleted it. */
@Entity
@Table(name = "network_messages", indexes = {
    @Index(name = "idx_network_messages_conversation", columnList = "conversation_id"),
    @Index(name = "idx_network_messages_sender", columnList = "sender_id")})
@Getter
@Setter
public class NetworkMessage extends BaseEntity {

    public static final int TEXT_MAX = 3000;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** Emptied once deleted. */
    @Column(name = "text", nullable = false, length = TEXT_MAX)
    private String text;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "edited_at")
    private Instant editedAt;

    /** Deleted for both sides, within the edit window: "Message deleted" stands in its place. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
