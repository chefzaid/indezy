package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.OfferSite;

import java.time.Instant;

/**
 * A job board of the feed and how its collection goes.
 *
 * @param openOffers offers of the site still open in the feed
 * @param failing whether the last run failed
 */
public record OfferSiteDto(OfferSite site, String name, String homeUrl, long openOffers, Instant lastSuccessAt,
                           boolean failing) {
}
