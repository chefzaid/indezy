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

/** Filters of the offer feed a workspace saved under a name, to come back to them and count what is new since. */
@Entity
@Table(name = "saved_offer_searches",
    uniqueConstraints = @UniqueConstraint(name = "uk_saved_offer_searches_freelance_name", columnNames = {"freelance_id", "name"}))
@Getter
@Setter
public class SavedOfferSearch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** The feed's filters and sort, as JSON. */
    @Column(name = "filters", nullable = false, columnDefinition = "TEXT")
    private String filters;

    /** When the workspace last opened the search: offers Indezy found after it are new. */
    @Column(name = "last_opened_at", nullable = false)
    private Instant lastOpenedAt;
}
