package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSearchSuggestionDto;
import dev.swirlit.indezy.dto.OfferSearchSuggestionsDto;
import dev.swirlit.indezy.dto.OfferSearchSuggestionsDto.ProfileField;
import dev.swirlit.indezy.dto.OfferSuggestionDismissalDto;
import dev.swirlit.indezy.model.OfferSuggestionDismissal;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferSpecifications;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.repository.OfferSuggestionDismissalRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SavedOfferSearchRepository;
import dev.swirlit.indezy.scraping.GeoLocator;
import dev.swirlit.indezy.util.FrenchGeography;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Searches suggested to a workspace from its profile and its pipeline (see docs/features.md#suggested-searches): the
 * candidates of {@link OfferSearchCandidates} that find offers in its feed, best first by the offers matching its
 * profile, leaving out the ones it saved or dismissed.
 */
@Service
@Transactional
public class OfferSearchSuggestionService {

    static final int MAX_SUGGESTIONS = 5;
    /** The opportunities looked at: added in the last 90 days, or won. */
    static final Duration PIPELINE_WINDOW = Duration.ofDays(90);
    /** How long a workspace's suggestions are kept before the feed is asked again, unless its profile changes. */
    static final Duration KEEP_FOR = Duration.ofMinutes(10);
    static final int NEW_WITHIN_DAYS = 7;
    /** Dismissals a workspace keeps: past it, the oldest go. */
    static final int MAX_DISMISSALS = 200;
    /** What a search is apart from its filters. */
    private static final List<String> NOT_FILTERS = List.of("view", "sort", "page", "size");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The suggestions found for some inputs, and when. */
    private record Found(OfferSearchCandidates.Inputs inputs, Instant at, List<OfferSearchSuggestionDto> suggestions) {
    }

    private final OfferSearchService offerSearch;
    private final FreelanceRepository freelances;
    private final ProjectRepository projects;
    private final JobOfferRepository offers;
    private final SavedOfferSearchRepository savedSearches;
    private final OfferSuggestionDismissalRepository dismissals;
    private final Clock clock;
    private final Map<Long, Found> found = new ConcurrentHashMap<>();

    @Autowired
    public OfferSearchSuggestionService(OfferSearchService offerSearch, FreelanceRepository freelances, ProjectRepository projects,
                                        JobOfferRepository offers, SavedOfferSearchRepository savedSearches,
                                        OfferSuggestionDismissalRepository dismissals) {
        this(offerSearch, freelances, projects, offers, savedSearches, dismissals, Clock.systemUTC());
    }

    OfferSearchSuggestionService(OfferSearchService offerSearch, FreelanceRepository freelances, ProjectRepository projects,
                                 JobOfferRepository offers, SavedOfferSearchRepository savedSearches,
                                 OfferSuggestionDismissalRepository dismissals, Clock clock) {
        this.offerSearch = offerSearch;
        this.freelances = freelances;
        this.projects = projects;
        this.offers = offers;
        this.savedSearches = savedSearches;
        this.dismissals = dismissals;
        this.clock = clock;
    }

    /** The searches suggested to the workspace now, and the profile fields it leaves empty. */
    @Transactional(readOnly = true)
    public OfferSearchSuggestionsDto suggestions(Long freelanceId) {
        Instant now = clock.instant();
        OfferMatch.Profile profile = offerSearch.profile(freelanceId);
        Optional<GeoLocator.Place> home = profile.origin() == null ? Optional.empty() : offerSearch.home(freelanceId);
        Optional<String> position = freelances.findProfilePosition(freelanceId).filter(title -> !title.isBlank());
        List<String> stacks = new ArrayList<>();
        List<Integer> rates = new ArrayList<>();
        LocalDateTime since = LocalDateTime.ofInstant(now.minus(PIPELINE_WINDOW), ZoneId.systemDefault());
        for (Object[] row : projects.findRecentStacksAndRates(freelanceId, since)) {
            stacks.add((String) row[0]);
            rates.add((Integer) row[1]);
        }
        OfferSearchCandidates.Inputs inputs = new OfferSearchCandidates.Inputs(profile.skills(), position.orElse(null),
            profile.targetRate() != null ? profile.targetRate() : OfferSearchCandidates.medianRate(rates),
            home.map(GeoLocator.Place::city).orElse(null), home.map(GeoLocator.Place::regionCode).orElse(null),
            profile.availableFrom(), OfferSearchCandidates.pipelineSkills(stacks, profile.skills()));

        Found known = found.get(freelanceId);
        if (known == null || !known.inputs().equals(inputs) || !known.at().plus(KEEP_FOR).isAfter(now)) {
            known = new Found(inputs, now, evaluate(freelanceId, inputs, profile, now));
            found.put(freelanceId, known);
        }
        Set<String> left = new HashSet<>(dismissals.findKeys(freelanceId));
        savedSearches.findByFreelanceIdOrderByNameAsc(freelanceId).forEach(saved -> left.add(keyOf(read(saved.getFilters()))));
        List<OfferSearchSuggestionDto> shown = known.suggestions().stream()
            .filter(suggestion -> !left.contains(suggestion.getKey()))
            .limit(MAX_SUGGESTIONS)
            .toList();
        return new OfferSearchSuggestionsDto(shown, missing(profile, position.isPresent()));
    }

    /** Dismisses a suggested search: searches with the same filters are not suggested again. */
    public void dismiss(Long freelanceId, OfferSearchRequest filters) {
        String key = keyOf(filters);
        if (dismissals.existsByFreelanceIdAndSuggestionKey(freelanceId, key)) {
            return;
        }
        List<OfferSuggestionDismissal> kept = dismissals.findByFreelanceIdOrderByDismissedAtAsc(freelanceId);
        if (kept.size() >= MAX_DISMISSALS) {
            dismissals.deleteAll(kept.subList(0, kept.size() - MAX_DISMISSALS + 1));
        }
        OfferSuggestionDismissal dismissal = new OfferSuggestionDismissal();
        dismissal.setFreelance(freelances.getReferenceById(freelanceId));
        dismissal.setSuggestionKey(key);
        filters.setView(OfferSearchRequest.View.FEED);
        filters.setPage(0);
        dismissal.setFilters(JSON.writeValueAsString(filters));
        dismissal.setDismissedAt(clock.instant());
        dismissals.save(dismissal);
    }

    /** The workspace's dismissed suggestions, for the account data export. */
    @Transactional(readOnly = true)
    public List<OfferSuggestionDismissalDto> export(Long freelanceId) {
        return dismissals.findByFreelanceIdOrderByDismissedAtAsc(freelanceId).stream()
            .map(dismissal -> new OfferSuggestionDismissalDto(read(dismissal.getFilters()), dismissal.getDismissedAt()))
            .toList();
    }

    /**
     * What tells a search's filters apart, whatever its sort, page or view and the order of its lists: the start of the
     * SHA-256 of its filters written canonically, unset ones left out.
     */
    static String keyOf(OfferSearchRequest filters) {
        ObjectNode node = JSON.valueToTree(filters);
        Map<String, String> canonical = new TreeMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            JsonNode value = field.getValue();
            if (NOT_FILTERS.contains(field.getKey()) || value.isNull() || value.isBoolean() && !value.booleanValue() ||
                value.isArray() && value.isEmpty()) {
                continue;
            }
            canonical.put(field.getKey(), value.isArray() ? sorted((ArrayNode) value) : value.toString());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }

    /** The candidates finding offers, with what they find, best first; each set of filters once. */
    private List<OfferSearchSuggestionDto> evaluate(Long freelanceId, OfferSearchCandidates.Inputs inputs, OfferMatch.Profile profile,
                                                    Instant now) {
        List<String> pair = List.of();
        long pairOffers = 0;
        for (List<String> skills : OfferSearchCandidates.pairs(inputs.skills())) {
            long count = count(with(new OfferSearchRequest(), search -> search.getSkills().addAll(skills)), freelanceId, profile, now);
            if (count > pairOffers) {
                pair = skills;
                pairOffers = count;
            }
        }
        Map<String, OfferSearchSuggestionDto> byKey = new LinkedHashMap<>();
        for (OfferSearchRequest search : OfferSearchCandidates.of(inputs, pair)) {
            String key = keyOf(search);
            if (byKey.containsKey(key)) {
                continue;
            }
            long open = count(search, freelanceId, profile, now);
            if (open == 0) {
                continue;
            }
            long good = profile.canMatch() ? count(with(copy(search), copy -> copy.setMatchesProfile(true)), freelanceId, profile, now) : 0;
            long fresh = count(with(copy(search), copy -> copy.setPublishedWithinDays(NEW_WITHIN_DAYS)), freelanceId, profile, now);
            byKey.put(key, new OfferSearchSuggestionDto(key, search, placeOf(search, inputs), open, good, fresh));
        }
        return byKey.values().stream()
            .sorted(Comparator.comparingLong(OfferSearchSuggestionDto::getGoodMatches)
                .thenComparingLong(OfferSearchSuggestionDto::getOffers).reversed())
            .toList();
    }

    /** The city a distance is measured from, or the region kept. */
    private static String placeOf(OfferSearchRequest search, OfferSearchCandidates.Inputs inputs) {
        if (search.getRadiusKm() != null) {
            return inputs.city();
        }
        if (search.getRegions().isEmpty()) {
            return null;
        }
        return FrenchGeography.region(search.getRegions().get(0)).map(FrenchGeography.Region::name).orElse(null);
    }

    private long count(OfferSearchRequest search, Long freelanceId, OfferMatch.Profile profile, Instant now) {
        return offers.count(JobOfferSpecifications.matching(search, freelanceId, profile, now));
    }

    private static List<ProfileField> missing(OfferMatch.Profile profile, boolean hasPosition) {
        List<ProfileField> missing = new ArrayList<>();
        if (!profile.canMatch()) {
            missing.add(ProfileField.SKILLS);
        }
        if (!hasPosition) {
            missing.add(ProfileField.POSITION);
        }
        if (profile.origin() == null) {
            missing.add(ProfileField.CITY);
        }
        if (profile.targetRate() == null) {
            missing.add(ProfileField.TARGET_RATE);
        }
        return missing;
    }

    private static String sorted(ArrayNode values) {
        List<String> items = new ArrayList<>();
        values.forEach(value -> items.add(value.toString()));
        items.sort(String::compareTo);
        return items.toString();
    }

    private static OfferSearchRequest with(OfferSearchRequest search, Consumer<OfferSearchRequest> change) {
        change.accept(search);
        return search;
    }

    private static OfferSearchRequest copy(OfferSearchRequest search) {
        return read(JSON.writeValueAsString(search));
    }

    private static OfferSearchRequest read(String filters) {
        return JSON.readValue(filters, OfferSearchRequest.class);
    }
}
