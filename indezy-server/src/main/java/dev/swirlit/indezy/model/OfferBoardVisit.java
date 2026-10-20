package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** When a workspace last visited a site Indezy does not read (see {@link OfferLeftOutSite.Kind#VISIT}). */
@Entity
@Table(name = "offer_board_visits",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_board_visits_freelance_site", columnNames = {"freelance_id", "site"}))
@Getter
@Setter
public class OfferBoardVisit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @Enumerated(EnumType.STRING)
    @Column(name = "site", nullable = false, length = 40)
    private OfferLeftOutSite site;

    @Column(name = "visited_at", nullable = false)
    private Instant visitedAt;
}
