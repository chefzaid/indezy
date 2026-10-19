package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferSite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the readers: claims each due site, reads it page after page through its {@link PageFetcher},
 * normalizes and stores every page, and records how the run went. One site at a time, on the caller's
 * thread.
 */
@Slf4j
@Service
public class OfferCollector {

    /** What one run read. */
    public record RunResult(int pages, int found, int created) {
    }

    private final Map<OfferSite, OfferReader> readers = new EnumMap<>(OfferSite.class);
    private final SiteHttpClient http;
    private final OfferNormalizer normalizer;
    private final OfferStore store;
    private final OfferSiteRuns runs;
    private final OfferProperties properties;
    private final Clock clock;

    @Autowired
    public OfferCollector(List<OfferReader> readers, SiteHttpClient http, OfferNormalizer normalizer,
                          OfferStore store, OfferSiteRuns runs, OfferProperties properties) {
        this(readers, http, normalizer, store, runs, properties, Clock.systemUTC());
    }

    OfferCollector(List<OfferReader> readers, SiteHttpClient http, OfferNormalizer normalizer, OfferStore store,
                   OfferSiteRuns runs, OfferProperties properties, Clock clock) {
        readers.forEach(reader -> this.readers.put(reader.site(), reader));
        this.http = http;
        this.normalizer = normalizer;
        this.store = store;
        this.runs = runs;
        this.properties = properties;
        this.clock = clock;
    }

    /** Reads every site that is due and that no other run holds. */
    public void collectDueSites() {
        for (OfferSite site : readers.keySet()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (runs.claim(site, clock.instant())) {
                collect(site);
            }
        }
    }

    /** Reads one site now; a failure is recorded on the site, never thrown. */
    public RunResult collect(OfferSite site) {
        OfferReader reader = readers.get(site);
        SiteBatch batch = new SiteBatch(site, reader);
        try {
            reader.read(http.fetcherFor(reader.domain()), batch);
            runs.succeeded(site, clock.instant(), batch.found, batch.created);
            log.info("Collected {}: {} pages, {} freelance offers, {} new", site, batch.pages, batch.found, batch.created);
        } catch (IOException | RuntimeException e) {
            log.warn("Collecting {} failed after {} pages: {}", site, batch.pages, e.getMessage());
            runs.failed(site, clock.instant(), e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runs.failed(site, clock.instant(), "Interrupted");
        }
        return new RunResult(batch.pages, batch.found, batch.created);
    }

    /** One run of a site: normalizes and stores each page, sets aside the offers left out, counts. */
    private final class SiteBatch implements OfferBatch {
        private final OfferSite site;
        private final OfferReader reader;
        private int pages;
        private int found;
        private int created;

        private SiteBatch(OfferSite site, OfferReader reader) {
            this.site = site;
            this.reader = reader;
        }

        @Override
        public boolean add(List<ScrapedOffer> page) {
            Instant now = clock.instant();
            List<JobOffer> offers = new ArrayList<>();
            List<String> dropped = new ArrayList<>();
            for (ScrapedOffer scraped : page) {
                normalizer.normalize(site, scraped, now).ifPresentOrElse(offers::add, () -> dropped.add(scraped.getExternalId()));
            }
            int saved = store.save(site, offers);
            store.skip(site, dropped, now);
            pages++;
            found += offers.size();
            created += saved;
            boolean onlyKnown = !offers.isEmpty() && saved == 0;
            return !page.isEmpty() && pages < properties.maxPages() && !(reader.newestFirst() && onlyKnown);
        }

        @Override
        public Set<String> known(Collection<String> externalIds) {
            return store.known(site, externalIds);
        }
    }
}
