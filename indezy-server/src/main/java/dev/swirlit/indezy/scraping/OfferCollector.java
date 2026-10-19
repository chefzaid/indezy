package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferSite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        int[] tally = new int[3];
        try {
            reader.read(http.fetcherFor(reader.domain()), page -> {
                List<JobOffer> offers = page.stream()
                    .map(offer -> normalizer.normalize(site, offer, clock.instant()))
                    .flatMap(Optional::stream)
                    .toList();
                int created = store.save(site, offers);
                tally[0]++;
                tally[1] += offers.size();
                tally[2] += created;
                boolean onlyKnown = !offers.isEmpty() && created == 0;
                return !page.isEmpty() && tally[0] < properties.maxPages() && !(reader.newestFirst() && onlyKnown);
            });
            runs.succeeded(site, clock.instant(), tally[1], tally[2]);
            log.info("Collected {}: {} pages, {} freelance offers, {} new", site, tally[0], tally[1], tally[2]);
        } catch (IOException | RuntimeException e) {
            log.warn("Collecting {} failed after {} pages: {}", site, tally[0], e.getMessage());
            runs.failed(site, clock.instant(), e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runs.failed(site, clock.instant(), "Interrupted");
        }
        return new RunResult(tally[0], tally[1], tally[2]);
    }
}
