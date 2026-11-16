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

/** A member reacting to a post, once: a like, or another reaction. */
@Entity
@Table(name = "network_likes",
    indexes = @Index(name = "idx_network_likes_post", columnList = "post_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_likes", columnNames = {"post_id", "user_id"}))
@Getter
@Setter
public class NetworkLike extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The reaction; none (null, as before reactions) reads as a like. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reaction", length = 20)
    private NetworkReaction reaction;

    public NetworkReaction reactionOrLike() {
        return reaction == null ? NetworkReaction.LIKE : reaction;
    }
}
