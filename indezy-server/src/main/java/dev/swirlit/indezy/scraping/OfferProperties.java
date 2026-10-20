package dev.swirlit.indezy.scraping;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings of the job offer collection ({@code indezy.offers.*}).
 *
 * @param collectionEnabled whether the scheduler reads the job boards (off in tests and one-off runs)
 * @param tick how often the scheduler looks for a site that is due
 * @param requestDelay minimum pause between two requests to the same site
 * @param maxPages pages one run may read on a site
 * @param staleRunAfter after this long, a run that never finished (its server stopped) no longer holds its site
 * @param retentionDays offers published longer ago are removed unless a workspace saved, applied to or imported them
 * @param userAgent how Indezy introduces itself to the sites
 * @param geocodingEnabled whether places are looked up on {@code geocodingUrl} (the French government API)
 * @param geocodingUrl base URL of the geocoding API
 */
@ConfigurationProperties(prefix = "indezy.offers")
public record OfferProperties(
    @DefaultValue("false") boolean collectionEnabled,
    @DefaultValue("PT5M") Duration tick,
    @DefaultValue("PT2S") Duration requestDelay,
    @DefaultValue("10") int maxPages,
    @DefaultValue("PT1H") Duration staleRunAfter,
    @DefaultValue("90") int retentionDays,
    @DefaultValue("Mozilla/5.0 (compatible; IndezyBot/1.0; +https://indezy.swirlit.dev)") String userAgent,
    @DefaultValue("false") boolean geocodingEnabled,
    @DefaultValue("https://geo.api.gouv.fr") String geocodingUrl) {
}
