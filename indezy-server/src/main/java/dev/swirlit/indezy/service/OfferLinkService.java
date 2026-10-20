package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferLinkDto;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.enums.OfferLinkStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.OfferLinkReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes the link of an offer into the opportunity it would be (see docs/features.md#from-an-offer-link): the offer as
 * the feed shows it, and the project form's prefill, unless the mission is in the pipeline already.
 */
@Service
public class OfferLinkService {

    /** Offers a workspace may have read from their page in {@link #READ_WINDOW}, so pasting links cannot crawl a site. */
    static final int READS_PER_WINDOW = 20;
    static final Duration READ_WINDOW = Duration.ofHours(1);

    private final OfferLinkReader reader;
    private final OfferStateService offerStates;
    private final OfferImportService importService;
    private final OfferSearchService search;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> reads = new ConcurrentHashMap<>();

    @Autowired
    public OfferLinkService(OfferLinkReader reader, OfferStateService offerStates, OfferImportService importService, OfferSearchService search) {
        this(reader, offerStates, importService, search, Clock.systemUTC());
    }

    OfferLinkService(OfferLinkReader reader, OfferStateService offerStates, OfferImportService importService, OfferSearchService search,
                     Clock clock) {
        this.reader = reader;
        this.offerStates = offerStates;
        this.importService = importService;
        this.search = search;
        this.clock = clock;
    }

    public OfferLinkDto resolve(Long freelanceId, String link) {
        URI uri = OfferLinkReader.parse(link);
        Optional<OfferSite> site = reader.siteOf(uri);
        if (site.isEmpty()) {
            return new OfferLinkDto(OfferLinkStatus.UNSUPPORTED_SITE, null, null, null, null, null, reader.siteNames());
        }
        String siteName = site.get().getDisplayName();
        OfferLinkReader.Lookup lookup = reader.find(site.get(), uri, () -> countRead(freelanceId));
        if (lookup.status() != OfferLinkStatus.FOUND) {
            return new OfferLinkDto(lookup.status(), siteName, null, null, null, lookup.retryAt(), null);
        }
        Long offerId = lookup.offerId();
        OfferDto offer = search.get(freelanceId, offerId);
        Optional<Long> project = offerStates.pipelineProject(freelanceId, offerId);
        if (project.isPresent()) {
            return new OfferLinkDto(OfferLinkStatus.IN_PIPELINE, siteName, offer, project.get(), null, null, null);
        }
        return new OfferLinkDto(OfferLinkStatus.FOUND, siteName, offer, null, importService.draft(freelanceId, offerId), null, null);
    }

    /** Counts one offer read for the workspace, refused past {@link #READS_PER_WINDOW} in the last {@link #READ_WINDOW}. */
    private void countRead(Long freelanceId) {
        Instant now = clock.instant();
        Deque<Instant> recent = reads.computeIfAbsent(freelanceId, id -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(now.minus(READ_WINDOW))) {
                recent.pollFirst();
            }
            if (recent.size() >= READS_PER_WINDOW) {
                throw new TooManyRequestsException(String.format(ErrorMessages.OFFER_LINK_READS_LIMIT, READS_PER_WINDOW),
                    Duration.between(now, recent.peekFirst().plus(READ_WINDOW)));
            }
            recent.addLast(now);
        }
    }
}
