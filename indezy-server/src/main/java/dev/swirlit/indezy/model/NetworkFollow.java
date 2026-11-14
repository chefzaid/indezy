package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member following another, by account id: the follower reads the followed member's posts. */
@Entity
@Table(name = "network_follows",
    indexes = @Index(name = "idx_network_follows_followed", columnList = "followed_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_follows", columnNames = {"follower_id", "followed_id"}))
@Getter
@Setter
public class NetworkFollow extends BaseEntity {

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "followed_id", nullable = false)
    private Long followedId;
}
