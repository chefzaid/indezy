package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ApplicationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDate;

/** The workspace applied to an offer: on which day (Paris time) and how. */
@Embeddable
public record OfferApplication(
    @Column(name = "applied_on") LocalDate appliedOn,
    @Enumerated(EnumType.STRING) @Column(name = "applied_via", length = 20) ApplicationChannel via) {
}
