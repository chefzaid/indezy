package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member's vote in a poll: one choice, which they may change while the poll is open. */
@Entity
@Table(name = "network_poll_votes",
    indexes = @Index(name = "idx_network_poll_votes_post", columnList = "post_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_poll_votes", columnNames = {"post_id", "user_id"}))
@Getter
@Setter
public class NetworkPollVote extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The choice's place, from 0. */
    @Column(name = "choice", nullable = false)
    private int choice;
}
