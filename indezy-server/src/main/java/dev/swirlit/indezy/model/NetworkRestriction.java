package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member muting or blocking another, by account id; only the member who set it sees it. */
@Entity
@Table(name = "network_restrictions",
    indexes = @Index(name = "idx_network_restrictions_target", columnList = "target_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_restrictions", columnNames = {"owner_id", "target_id", "kind"}))
@Getter
@Setter
public class NetworkRestriction extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private NetworkRestrictionKind kind;
}
