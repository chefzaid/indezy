package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member finding a post useful, once: the second reaction, for knowledge posts. */
@Entity
@Table(name = "network_usefuls",
    indexes = @Index(name = "idx_network_usefuls_post", columnList = "post_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_usefuls", columnNames = {"post_id", "user_id"}))
@Getter
@Setter
public class NetworkUseful extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
