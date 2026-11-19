package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A member opening another's page, once a day per pair (the latest time kept): what **Who viewed my page** reads, 90 days long. */
@Entity
@Table(name = "network_page_views",
    uniqueConstraints = @UniqueConstraint(name = "uk_network_page_views", columnNames = {"viewer_id", "member_id", "viewed_on"}),
    indexes = @Index(name = "idx_network_page_views_member", columnList = "member_id, viewed_at"))
@Getter
@Setter
public class NetworkPageView extends BaseEntity {

    @Column(name = "viewer_id", nullable = false)
    private Long viewerId;

    /** The member whose page was opened. */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "viewed_on", nullable = false)
    private LocalDate viewedOn;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
