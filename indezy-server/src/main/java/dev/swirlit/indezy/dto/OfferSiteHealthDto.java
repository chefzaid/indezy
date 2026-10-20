package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;

import java.time.Instant;
import java.util.List;

/**
 * How the collection of each job board goes, for the site health page.
 *
 * @param sites          the boards Indezy reads, failing ones first, then by name
 * @param leftOut        the boards it does not read: to visit, matching services, then the ones left out (the page explains why)
 * @param refreshEnabled whether a site can be read on request (collection is on)
 */
public record OfferSiteHealthDto(List<Site> sites, List<LeftOut> leftOut, boolean refreshEnabled) {

    public enum Status {
        /** The last run succeeded. */
        OK,
        /** The last run failed; the site is tried again later and later. */
        FAILING,
        /** A run reads it now. */
        RUNNING,
        /** Never read yet. */
        WAITING
    }

    /**
     * @param collectedDay       offers first seen over the last 24 hours
     * @param collectedWeek      offers first seen over the last 7 days
     * @param openOffers         offers not expired
     * @param newestOfferAt      when its most recent offer was published
     * @param lastFound          freelance offers the last successful run read
     * @param lastNew            offers it saw for the first time
     * @param excluded           whether the workspace keeps it out of its feed
     * @param refreshAvailableAt when it can be read on request again; {@code null} when it can now
     */
    public record Site(OfferSite site, String name, String homeUrl, Status status, Instant lastRunAt, Instant lastSuccessAt,
                       Instant nextRunAt, String lastError, int consecutiveFailures, Integer lastFound, Integer lastNew,
                       long collectedDay, long collectedWeek, long openOffers, Instant newestOfferAt, boolean excluded,
                       Instant refreshAvailableAt) {
    }

    /**
     * @param kind      whether it is to visit, a matching service, or left out
     * @param access    what seeing its missions takes; {@code null} for a site left out
     * @param visitedAt when the workspace last visited it, if it did
     */
    public record LeftOut(OfferLeftOutSite site, String name, String homeUrl, OfferLeftOutSite.Kind kind, OfferLeftOutSite.Access access,
                          Instant visitedAt) {
    }

    public OfferSiteHealthDto {
        sites = List.copyOf(sites);
        leftOut = List.copyOf(leftOut);
    }
}
