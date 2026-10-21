package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.swirlit.indezy.model.enums.OfferSite;

import java.time.Instant;
import java.util.List;

/**
 * What a publisher (an intermediary or a client) puts in the feed (see docs/features.md#publisher-page): its open
 * missions, their median daily rate and age, how many of its missions of the last 90 days it published again under a
 * new reference, the sites it posts on, its newest open offers, and the workspace's client record and block of it.
 *
 * @param medianOpenDays how long its open offers have been published, the median, in days
 * @param missions       its missions published in the last 90 days, each once per site
 * @param republished    of those, the ones published again under a new reference
 * @param blockId        the workspace's block of the publisher, absent when its offers are in the feed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferPublisherDto(String key, String name, int openMissions, Integer medianRate, Integer medianOpenDays, int missions,
                                int republished, List<SiteOffers> sites, Client client, Long blockId, List<Summary> offers) {

    public OfferPublisherDto {
        sites = List.copyOf(sites);
        offers = List.copyOf(offers);
    }

    /** A site the publisher posts on, with its open offers there. */
    public record SiteOffers(OfferSite site, String siteName, int offers) {
    }

    /** The workspace's client record named like the publisher. */
    public record Client(Long id, String name, boolean intermediary) {
    }

    /** One of its open offers. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(Long id, String title, String siteName, Integer rateMin, Integer rateMax, String city, Instant publishedAt) {
    }
}
