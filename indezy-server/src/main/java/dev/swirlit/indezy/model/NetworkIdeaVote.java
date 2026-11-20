package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member's vote for an idea of the ideas board, once each. */
@Entity
@Table(name = "network_idea_votes",
    uniqueConstraints = @UniqueConstraint(name = "uk_network_idea_votes", columnNames = {"idea_id", "user_id"}))
@Getter
@Setter
public class NetworkIdeaVote extends BaseEntity {

    @Column(name = "idea_id", nullable = false)
    private Long ideaId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
