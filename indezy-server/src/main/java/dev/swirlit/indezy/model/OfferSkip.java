package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.OfferSite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An offer a reader found but the feed left out (closed to freelancers, too old, incomplete). Remembered for a
 * while so sites read one page per offer do not fetch it again on every run.
 */
@Entity
@Table(name = "offer_skips",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_skips_site_external_id", columnNames = {"site", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
public class OfferSkip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "site", nullable = false, length = 40)
    private OfferSite site;

    @Column(name = "external_id", nullable = false, length = 191)
    private String externalId;

    @Column(name = "skipped_at", nullable = false)
    private Instant skippedAt;

    public OfferSkip(OfferSite site, String externalId, Instant skippedAt) {
        this.site = site;
        this.externalId = externalId;
        this.skippedAt = skippedAt;
    }
}
