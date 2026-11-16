package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A conversation between members (see docs/features.md#member-network): between two, one at most for the same two, or a
 * group with a name (no pair key) of up to 20 members who followed its creator.
 */
@Entity
@Table(name = "network_conversations", uniqueConstraints = @UniqueConstraint(name = "uk_network_conversations_pair", columnNames = "pair_key"))
@Getter
@Setter
public class NetworkConversation extends BaseEntity {

    /** Who opened it. */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /** The two members' account ids, the smaller first ({@code 4:9}), so a second conversation between them cannot exist. */
    @Column(name = "pair_key", length = 41)
    private String pairKey;

    /** A group's name; none for a conversation between two members. */
    @Column(name = "title", length = 80)
    private String title;

    /** When its latest message was sent; none before the first. */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    public boolean isGroup() {
        return pairKey == null;
    }

    public static String pairKey(Long first, Long second) {
        return Math.min(first, second) + ":" + Math.max(first, second);
    }
}
