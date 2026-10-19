package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.OfferSite;

/** A copy of an offer on another site: where else the same mission is published. */
public record OfferCopyDto(Long id, OfferSite site, String siteName, String url) {
}
