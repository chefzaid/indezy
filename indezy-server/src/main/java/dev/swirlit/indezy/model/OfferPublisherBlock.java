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

/**
 * A publisher (an intermediary or a client) whose offers a workspace keeps out of its feed, its saved searches and its
 * market snapshot, matched on the offers' {@link JobOffer#getCompanyKey() company key}.
 */
@Entity
@Table(name = "offer_publisher_blocks",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_publisher_blocks_freelance_key", columnNames = {"freelance_id", "publisher_key"}))
@Getter
@Setter
public class OfferPublisherBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    /** The name as the workspace blocked it. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "publisher_key", nullable = false, length = 200)
    private String publisherKey;
}
