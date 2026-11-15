package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member adding a mission passed on in the network to their pipeline, once, and whether its author may know who. */
@Entity
@Table(name = "network_mission_takes",
    indexes = @Index(name = "idx_network_mission_takes_post", columnList = "post_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_mission_takes", columnNames = {"post_id", "user_id"}))
@Getter
@Setter
public class NetworkMissionTake extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The opportunity it became. */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** The member lets the mission's author see their name among those who took it. */
    @Column(name = "named", nullable = false)
    private boolean named;
}
