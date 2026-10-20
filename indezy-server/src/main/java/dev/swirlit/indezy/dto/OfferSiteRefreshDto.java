package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.OfferSite;

import java.time.Instant;

/** A reading of a job board started on request, and when the board can be asked for again. */
public record OfferSiteRefreshDto(OfferSite site, Instant refreshAvailableAt) {
}
