package dev.swirlit.indezy.dto;

/** A value the feed can be filtered on and how many open offers have it. */
public record OfferFacetDto(String value, long offers) {
}
