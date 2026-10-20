package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.OfferSite;
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

/** A job board a workspace keeps out of its offer feed, its saved searches and its market snapshot. */
@Entity
@Table(name = "offer_site_exclusions",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_site_exclusions_freelance_site", columnNames = {"freelance_id", "site"}))
@Getter
@Setter
public class OfferSiteExclusion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @Enumerated(EnumType.STRING)
    @Column(name = "site", nullable = false, length = 40)
    private OfferSite site;
}
