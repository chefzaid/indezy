package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.scraping.OfferBatch;
import dev.swirlit.indezy.scraping.OfferReader;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.SiteResponseException;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A site that lists its offers (a sitemap, an API) and describes each apart: on its own page
 * ({@link DetailPagesReader}) or through its API. Each run reads only the offers Indezy does not hold yet, most
 * recently changed first, ten per batch, so the page budget caps the requests and the backlog is read over
 * successive runs.
 */
abstract class OfferDetailsReader implements OfferReader {

    /** Offer pages fetched per batch. */
    static final int PAGES_PER_BATCH = 10;
    /** Identifiers asked about at once. */
    private static final int KNOWN_CHUNK = 500;

    /** One offer of the list: its page, its identifier on the site, and what the list says of it. */
    record Entry(String url, String externalId, Instant lastModified, String title, Instant publishedAt) {
        Entry(String url, String externalId, Instant lastModified) {
            this(url, externalId, lastModified, null, null);
        }
    }

    /** Every offer the site lists now. */
    abstract List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException;

    /** The offer of an entry, or {@code null} when its page holds none. */
    abstract ScrapedOffer details(PageFetcher fetcher, Entry entry) throws IOException, InterruptedException;

    /** The identifier of the offer whose page is {@code url}, or {@code null} when it is no offer page of the site. */
    String externalId(String url) {
        return null;
    }

    @Override
    public final Optional<String> offerId(String url) {
        return Optional.ofNullable(externalId(url));
    }

    @Override
    public final Optional<ScrapedOffer> readOne(PageFetcher fetcher, String url) throws IOException, InterruptedException {
        String externalId = externalId(url);
        return externalId == null ? Optional.empty() : Optional.of(fetch(fetcher, new Entry(url, externalId, null)));
    }

    /** Every page read is an offer Indezy does not hold, so only the page budget ends a run. */
    @Override
    public boolean newestFirst() {
        return false;
    }

    @Override
    public final void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        List<Entry> unknown = unknown(entries(fetcher), batch);
        unknown.sort(Comparator.comparing(Entry::lastModified, Comparator.nullsLast(Comparator.reverseOrder())));
        for (int start = 0; start < unknown.size(); start += PAGES_PER_BATCH) {
            List<ScrapedOffer> offers = new ArrayList<>();
            for (Entry entry : unknown.subList(start, Math.min(unknown.size(), start + PAGES_PER_BATCH))) {
                offers.add(fetch(fetcher, entry));
            }
            if (!batch.add(offers)) {
                return;
            }
        }
    }

    private ScrapedOffer fetch(PageFetcher fetcher, Entry entry) throws IOException, InterruptedException {
        try {
            ScrapedOffer offer = details(fetcher, entry);
            if (offer != null) {
                return offer.toBuilder().externalId(entry.externalId()).url(entry.url()).build();
            }
        } catch (SiteResponseException e) {
            if (e.getStatus() != 404 && e.getStatus() != 410) {
                throw e;
            }
        }
        // A page gone or without an offer is handed on as closed, so it is set aside and not fetched again.
        return ScrapedOffer.builder().externalId(entry.externalId()).url(entry.url()).freelance(false).build();
    }

    /** What follows {@code marker} in {@code url} up to its end or its next slash, or {@code null} when there is nothing. */
    static String after(String url, String marker) {
        int start = url.indexOf(marker);
        if (start < 0) {
            return null;
        }
        String rest = url.substring(start + marker.length());
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        return id.isBlank() ? null : id;
    }

    private static List<Entry> unknown(List<Entry> entries, OfferBatch batch) {
        Set<String> seen = new HashSet<>();
        List<Entry> distinct = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.externalId() != null && seen.add(entry.externalId())) {
                distinct.add(entry);
            }
        }
        Set<String> known = new HashSet<>();
        for (int start = 0; start < distinct.size(); start += KNOWN_CHUNK) {
            List<String> ids = distinct.subList(start, Math.min(distinct.size(), start + KNOWN_CHUNK)).stream()
                .map(Entry::externalId).toList();
            known.addAll(batch.known(ids));
        }
        List<Entry> unknown = new ArrayList<>();
        for (Entry entry : distinct) {
            if (!known.contains(entry.externalId())) {
                unknown.add(entry);
            }
        }
        return unknown;
    }
}
