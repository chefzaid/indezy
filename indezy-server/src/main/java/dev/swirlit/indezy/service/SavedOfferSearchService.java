package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferAlertsDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.SavedOfferSearchDto;
import dev.swirlit.indezy.dto.SavedOfferSearchRequest;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.SavedOfferSearch;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferSpecifications;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.repository.SavedOfferSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The offer feed's saved searches: filters a workspace keeps under a name, each with the offers Indezy found for it
 * since the workspace last opened it, and the total behind the badge on Offers.
 */
@Service
@Transactional
public class SavedOfferSearchService {

    /** Saved searches a workspace keeps at most. */
    static final int MAX_SEARCHES = 20;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SavedOfferSearchRepository searches;
    private final JobOfferRepository offers;
    private final FreelanceRepository freelances;
    private final OfferSearchService offerSearch;
    private final Clock clock;

    @Autowired
    public SavedOfferSearchService(SavedOfferSearchRepository searches, JobOfferRepository offers, FreelanceRepository freelances,
                                   OfferSearchService offerSearch) {
        this(searches, offers, freelances, offerSearch, Clock.systemUTC());
    }

    SavedOfferSearchService(SavedOfferSearchRepository searches, JobOfferRepository offers, FreelanceRepository freelances,
                            OfferSearchService offerSearch, Clock clock) {
        this.searches = searches;
        this.offers = offers;
        this.freelances = freelances;
        this.offerSearch = offerSearch;
        this.clock = clock;
    }

    /** The workspace's saved searches by name, each with its new offers. */
    @Transactional(readOnly = true)
    public List<SavedOfferSearchDto> list(Long freelanceId) {
        Instant now = clock.instant();
        OfferMatch.Profile profile = offerSearch.profile(freelanceId);
        return searches.findByFreelanceIdOrderByNameAsc(freelanceId).stream()
            .map(search -> toDto(search, offers.count(newOffers(search, freelanceId, profile, now))))
            .toList();
    }

    /** Saves the feed's current filters (not its view or page) under a new name. */
    public SavedOfferSearchDto create(Long freelanceId, SavedOfferSearchRequest request) {
        String name = request.getName().strip();
        if (searches.countByFreelanceId(freelanceId) >= MAX_SEARCHES) {
            throw new InvalidStateException(String.format(ErrorMessages.SAVED_SEARCHES_LIMIT, MAX_SEARCHES));
        }
        if (searches.existsByFreelanceIdAndNameIgnoreCase(freelanceId, name)) {
            throw new InvalidStateException(String.format(ErrorMessages.SAVED_SEARCH_NAME_TAKEN, name));
        }
        OfferSearchRequest filters = request.getFilters();
        filters.setView(OfferSearchRequest.View.FEED);
        filters.setPage(0);
        SavedOfferSearch search = new SavedOfferSearch();
        search.setFreelance(freelances.findById(freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_NOT_FOUND, freelanceId))));
        search.setName(name);
        search.setFilters(JSON.writeValueAsString(filters));
        search.setLastOpenedAt(clock.instant());
        return toDto(searches.save(search), 0);
    }

    /** Opens a saved search: only what Indezy finds from now on is new for it. */
    public SavedOfferSearchDto open(Long freelanceId, Long id) {
        SavedOfferSearch search = find(freelanceId, id);
        search.setLastOpenedAt(clock.instant());
        return toDto(search, 0);
    }

    public void delete(Long freelanceId, Long id) {
        searches.delete(find(freelanceId, id));
    }

    /** The offers new for any of the workspace's saved searches. */
    @Transactional(readOnly = true)
    public OfferAlertsDto alerts(Long freelanceId) {
        List<SavedOfferSearch> saved = searches.findByFreelanceIdOrderByNameAsc(freelanceId);
        if (saved.isEmpty()) {
            return new OfferAlertsDto(0);
        }
        Instant now = clock.instant();
        OfferMatch.Profile profile = offerSearch.profile(freelanceId);
        return new OfferAlertsDto(offers.count(Specification.anyOf(saved.stream()
            .map(search -> newOffers(search, freelanceId, profile, now))
            .toList())));
    }

    /** The workspace's saved searches, for the account data export. */
    @Transactional(readOnly = true)
    public List<SavedOfferSearchDto> export(Long freelanceId) {
        return searches.findByFreelanceIdOrderByNameAsc(freelanceId).stream().map(search -> toDto(search, 0)).toList();
    }

    /** The feed's offers for the search that Indezy found after the search was last opened (one per mission). */
    private static Specification<JobOffer> newOffers(SavedOfferSearch search, Long freelanceId, OfferMatch.Profile profile,
                                                     Instant now) {
        Instant since = search.getLastOpenedAt();
        return JobOfferSpecifications.matching(filters(search), freelanceId, profile, now)
            .and((root, query, cb) -> cb.greaterThan(root.get("firstSeenAt"), since));
    }

    private SavedOfferSearch find(Long freelanceId, Long id) {
        return searches.findByIdAndFreelanceId(id, freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.SAVED_SEARCH_NOT_FOUND, id)));
    }

    private static OfferSearchRequest filters(SavedOfferSearch search) {
        return JSON.readValue(search.getFilters(), OfferSearchRequest.class);
    }

    private static SavedOfferSearchDto toDto(SavedOfferSearch search, long newOffers) {
        return new SavedOfferSearchDto(search.getId(), search.getName(), filters(search), search.getLastOpenedAt(), newOffers);
    }
}
