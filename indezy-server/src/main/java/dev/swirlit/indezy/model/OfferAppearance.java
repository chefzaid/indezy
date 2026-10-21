package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.OfferSite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A reference a site published a mission under (its title and publisher, {@code JobOffer#publisherKey}), kept 90 days
 * even once the offer is removed, to tell how often a mission came back (see docs/features.md#mission-history).
 */
@Entity
@Table(name = "offer_appearances",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_appearances_site_external_id", columnNames = {"site", "external_id"}),
    indexes = @Index(name = "idx_offer_appearances_publisher_key", columnList = "publisher_key"))
@Getter
@Setter
public class OfferAppearance extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "site", nullable = false, length = 40)
    private OfferSite site;

    @Column(name = "publisher_key", nullable = false, length = 64)
    private String publisherKey;

    @Column(name = "external_id", nullable = false, length = 191)
    private String externalId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;
}
