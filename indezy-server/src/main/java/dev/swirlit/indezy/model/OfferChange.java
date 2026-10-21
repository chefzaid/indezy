package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.OfferChangeField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Something its site changed on a stored offer, removed with the offer (see docs/features.md#mission-history). */
@Entity
@Table(name = "job_offer_changes", indexes = @Index(name = "idx_job_offer_changes_offer", columnList = "offer_id"))
@Getter
@Setter
public class OfferChange extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", nullable = false)
    private JobOffer offer;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 20)
    private OfferChangeField field;

    @Column(name = "before_value", length = 40)
    private String before;

    @Column(name = "after_value", length = 40)
    private String after;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
