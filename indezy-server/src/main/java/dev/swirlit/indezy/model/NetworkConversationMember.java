package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A member of a conversation, with what is theirs alone: what they read, and whether they muted or archived it. */
@Entity
@Table(name = "network_conversation_members",
    indexes = @Index(name = "idx_network_conversation_members_user", columnList = "user_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_conversation_members", columnNames = {"conversation_id", "user_id"}))
@Getter
@Setter
public class NetworkConversationMember extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Until when they read it; none while they never opened it. */
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    /** Its messages count in no unread total and no email; none (null) reads as not muted. */
    @Column(name = "muted")
    private Boolean muted;

    /** Out of their list until a new message comes; none (null) reads as not archived. */
    @Column(name = "archived")
    private Boolean archived;

    public boolean isMuted() {
        return Boolean.TRUE.equals(muted);
    }

    public boolean isArchived() {
        return Boolean.TRUE.equals(archived);
    }
}
