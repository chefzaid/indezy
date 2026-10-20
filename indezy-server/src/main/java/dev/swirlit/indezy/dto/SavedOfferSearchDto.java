package dev.swirlit.indezy.dto;

import java.time.Instant;

/** A saved search with how many offers Indezy found for it since it was last opened. */
public record SavedOfferSearchDto(Long id, String name, OfferSearchRequest filters, Instant lastOpenedAt, long newOffers) {
}
