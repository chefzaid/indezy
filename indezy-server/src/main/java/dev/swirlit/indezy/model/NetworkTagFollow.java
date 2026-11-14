package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member following a #tag: posts naming it reach their home feed. */
@Entity
@Table(name = "network_tag_follows", uniqueConstraints = @UniqueConstraint(name = "uk_network_tag_follows", columnNames = {"user_id", "tag"}))
@Getter
@Setter
public class NetworkTagFollow extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Lower-case, without the #. */
    @Column(name = "tag", nullable = false, length = 40)
    private String tag;
}
