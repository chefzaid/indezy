package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** What the AI made of a feed offer for a workspace: whether it fits, in one line, and its deal-breakers. Kept to be paid once. */
@Entity
@Table(name = "offer_ai_verdicts", uniqueConstraints = @UniqueConstraint(name = "uk_offer_ai_verdicts", columnNames = {"freelance_id", "offer_id"}))
@Getter
@Setter
public class OfferVerdict extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    @Column(name = "good_fit", nullable = false)
    private boolean goodFit;

    @Column(name = "verdict", nullable = false, length = 300)
    private String verdict;

    /** One per line. */
    @Column(name = "deal_breakers", length = 1000)
    private String dealBreakers;

    /** The end client the offer is likely for, as the AI named or described it from the offer; null when unknown. */
    @Column(name = "likely_client", length = 120)
    private String likelyClient;
}
