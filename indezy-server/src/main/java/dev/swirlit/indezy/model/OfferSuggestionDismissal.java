package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A suggested offer search the workspace dismissed: the same filters are not suggested again. */
@Entity
@Table(name = "offer_suggestion_dismissals",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_suggestion_dismissals_key", columnNames = {"freelance_id", "suggestion_key"}))
@Getter
@Setter
public class OfferSuggestionDismissal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    /** The filters' key (see {@code OfferSearchSuggestionService#keyOf}). */
    @Column(name = "suggestion_key", nullable = false, length = 32)
    private String suggestionKey;

    /** The filters dismissed, as JSON. */
    @Column(name = "filters", nullable = false, columnDefinition = "TEXT")
    private String filters;

    @Column(name = "dismissed_at", nullable = false)
    private Instant dismissedAt;
}
