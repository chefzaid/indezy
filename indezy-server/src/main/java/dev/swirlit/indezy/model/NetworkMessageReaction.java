package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkReaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member's reaction to a message, one each. */
@Entity
@Table(name = "network_message_reactions",
    indexes = @Index(name = "idx_network_message_reactions_message", columnList = "message_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_message_reactions", columnNames = {"message_id", "user_id"}))
@Getter
@Setter
public class NetworkMessageReaction extends BaseEntity {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction", nullable = false, length = 20)
    private NetworkReaction reaction;
}
