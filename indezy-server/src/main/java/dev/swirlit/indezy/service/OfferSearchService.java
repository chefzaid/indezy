package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferCommuteDto;
import dev.swirlit.indezy.dto.OfferCopyDto;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferHistoryDto;
import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.dto.OfferPublisherSpreadDto;
import dev.swirlit.indezy.dto.OfferRegionDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSiteDto;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.OfferSuspicion;
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferSpecifications;
import dev.swirlit.indezy.repository.JobOfferSpecifications.Origin;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.repository.OfferSiteExclusionRepository;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import dev.swirlit.indezy.scraping.GeoLocator;
import dev.swirlit.indezy.util.ExpiringValue;
import dev.swirlit.indezy.util.FrenchGeography;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the offer feed. Deliberately without a transaction of its own: locating the profile's city may
 * write to the geocoding cache, and every read below is a single repository query (no lazy loading).
 */
@Service
@RequiredArgsConstructor
public class OfferSearchService {

    static final int EXCERPT_LENGTH = 280;
    private static final int TOP_SKILLS = 20;
    /** How long the open offers per site and per skill are kept in memory: every feed asks them, and they move slowly. */
    static final Duration FACETS_KEEP_FOR = Duration.ofMinutes(1);
    private static final double EARTH_RADIUS_KM = 6371.0;
    /** Where "today" is read, against the profile's availability date. */
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** How much longer than the longest commute a reachable offer may take. */
    static final double REACHABLE_COMMUTE_FACTOR = 1.5;

    private final JobOfferRepository offers;
    private final JobOfferStateRepository states;
    private final OfferSiteStatusRepository statuses;
    private final FreelanceRepository freelances;
    private final GeoLocator geoLocator;
    private final OfferSiteExclusionRepository exclusions;
    private final OfferRateBenchmarkService benchmarks;
    private final OfferHistoryService history;
    private final Clock clock;
    /** Records the profile's terms in the offers; none until Spring sets it (unit tests build the service by hand). */
    private OfferMentionIndexer mentions;
    private final ExpiringValue<Map<OfferSite, Long>> openBySite = new ExpiringValue<>(FACETS_KEEP_FOR, this::countOpenBySite);
    private final ExpiringValue<List<SkillCount>> openSkills = new ExpiringValue<>(FACETS_KEEP_FOR, this::countOpenSkills);

    /** A skill of the open offers (spellings that differ only by case count as one), in lower case to match a prefix. */
    private record SkillCount(String skill, String lowerCase, long offers) {
    }

    /** One page of offers matching the request, for {@code freelanceId}'s workspace (or none). */
    public OfferPageDto search(Long freelanceId, OfferSearchRequest request) {
        return search(freelanceId, request, null, PageRequest.of(request.getPage(), request.getSize()));
    }

    /**
     * The first {@code size} offers of a search, in its order, that Indezy found after {@code since} and up to
     * {@code until}: a saved search's email.
     */
    public OfferPageDto foundBetween(Long freelanceId, OfferSearchRequest request, Instant since, Instant until, int size) {
        Specification<JobOffer> found = (root, query, cb) ->
            cb.and(cb.greaterThan(root.get("firstSeenAt"), since), cb.lessThanOrEqualTo(root.get("firstSeenAt"), until));
        return search(freelanceId, request, found, PageRequest.of(0, size));
    }

    private OfferPageDto search(Long freelanceId, OfferSearchRequest request, Specification<JobOffer> within, PageRequest pageRequest) {
        Instant now = clock.instant();
        OfferMatch.Profile profile = profileFor(freelanceId, request);
        Specification<JobOffer> matching = JobOfferSpecifications.matching(request, freelanceId, profile, now);
        // Another page or sort of the same filters: the caller already knows their total (about 100 ms of counting).
        Page<JobOffer> page = within == null && request.getTotal() != null ? offers.findPage(matching, pageRequest, request.getTotal()) :
            offers.findAll(within == null ? matching : matching.and(within), pageRequest);
        List<Long> ids = page.getContent().stream().map(JobOffer::getId).toList();
        Map<Long, List<String>> skills = skillsOf(ids);
        Map<Long, List<JobOffer>> copies = copiesOf(page.getContent());
        Map<Long, OfferStateDto> stateByOffer = statesOf(freelanceId, page.getContent(), copies);
        Set<OfferSite> excluded = excludedSites(freelanceId);
        Map<Long, OfferHistoryDto> histories = history.of(page.getContent(), now);
        List<OfferDto> content = page.getContent().stream()
            .map(offer -> toDto(offer, skills.get(offer.getId()), stateByOffer.get(offer.getId()),
                alsoOn(offer, copies.getOrDefault(offer.getId(), List.of()), excluded, now).with(histories.get(offer.getId())), profile, now,
                false))
            .toList();
        return new OfferPageDto(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
            profile.origin() != null, profile.canMatch());
    }

    /** One offer with its full description. */
    public OfferDto get(Long freelanceId, Long id) {
        JobOffer offer = offers.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.OFFER_NOT_FOUND, id)));
        OfferMatch.Profile profile = freelanceId == null ? OfferMatch.Profile.NONE : profile(freelanceId);
        Map<Long, List<JobOffer>> copies = copiesOf(List.of(offer));
        Instant now = clock.instant();
        return toDto(offer, skillsOf(List.of(id)).get(id), statesOf(freelanceId, List.of(offer), copies).get(id),
            alsoOn(offer, copies.getOrDefault(id, List.of()), excludedSites(freelanceId), now).with(history.of(List.of(offer), now).get(id)),
            profile, now, true);
    }

    /** Every job board of the feed, with its open offers, collection health and whether the workspace (if any) keeps it. */
    public List<OfferSiteDto> sites(Long freelanceId) {
        Set<OfferSite> excluded = excludedSites(freelanceId);
        Map<OfferSite, OfferSiteStatus> byStatus = statuses.findAll().stream()
            .collect(Collectors.toMap(OfferSiteStatus::getSite, Function.identity()));
        Map<OfferSite, Long> open = openBySite.get(clock.instant());
        return Arrays.stream(OfferSite.values())
            .map(site -> {
                OfferSiteStatus status = byStatus.get(site);
                return new OfferSiteDto(site, site.getDisplayName(), site.getHomeUrl(), open.getOrDefault(site, 0L),
                    status == null ? null : status.getLastSuccessAt(), status != null && status.getConsecutiveFailures() > 0,
                    excluded.contains(site));
            })
            .toList();
    }

    /** The open offers' most frequent skills starting with {@code prefix}, case aside. */
    public List<OfferFacetDto> skills(String prefix) {
        String start = prefix == null ? "" : prefix.strip().toLowerCase(Locale.ROOT);
        return openSkills.get(clock.instant()).stream()
            .filter(skill -> skill.lowerCase().startsWith(start))
            .limit(TOP_SKILLS)
            .map(skill -> new OfferFacetDto(skill.skill(), skill.offers()))
            .toList();
    }

    private Map<OfferSite, Long> countOpenBySite() {
        Map<OfferSite, Long> open = new EnumMap<>(OfferSite.class);
        for (Object[] row : offers.countOpenBySite(clock.instant())) {
            open.put((OfferSite) row[0], (Long) row[1]);
        }
        return open;
    }

    /** Every skill of the open offers, the most frequent first, then alphabetically. */
    private List<SkillCount> countOpenSkills() {
        return offers.countOpenSkills(clock.instant()).stream()
            .map(row -> new SkillCount((String) row[0], ((String) row[0]).toLowerCase(Locale.ROOT), (Long) row[1]))
            .sorted(Comparator.comparingLong(SkillCount::offers).reversed().thenComparing(SkillCount::lowerCase))
            .toList();
    }

    /** French regions with their departments, for the location filter. */
    public List<OfferRegionDto> regions() {
        return FrenchGeography.regions().stream()
            .map(region -> new OfferRegionDto(region.code(), region.name(), FrenchGeography.departmentsOf(region.code()).stream()
                .map(department -> new OfferRegionDto.Department(department.code(), department.name()))
                .toList()))
            .toList();
    }

    /** The profile a search runs with (none without a workspace); a distance filter needs its city. */
    public OfferMatch.Profile profileFor(Long freelanceId, OfferSearchRequest request) {
        OfferMatch.Profile profile = freelanceId == null ? OfferMatch.Profile.NONE : profile(freelanceId);
        if (request.getRadiusKm() != null && profile.origin() == null) {
            throw new IllegalArgumentException(ErrorMessages.OFFER_DISTANCE_NEEDS_CITY);
        }
        return profile;
    }

    /** What the workspace's offers are measured against: the profile's skills, target daily rate, city and availability. */
    public OfferMatch.Profile profile(Long freelanceId) {
        Optional<GeoLocator.Place> home = home(freelanceId);
        OfferMatch.Profile profile = new OfferMatch.Profile(freelances.findProfileSkills(freelanceId),
            freelances.findTargetDailyRate(freelanceId).orElse(null), home.map(OfferSearchService::origin).orElse(null),
            freelances.findAvailabilityDate(freelanceId).orElse(null), LocalDate.now(clock.withZone(PARIS)));
        profile = withLongestCommute(profile, freelanceId, home.map(CommuteService::ileDeFrance).orElse(false));
        return profile.canMatch() && mentions != null ? profile.recording(mentions.indexed(profile.terms())) : profile;
    }

    /**
     * With a longest commute in the profile, an offer is near when its estimated trip at rush hour fits it by either mode,
     * and reachable within one and a half times it.
     */
    private OfferMatch.Profile withLongestCommute(OfferMatch.Profile profile, Long freelanceId, boolean ileDeFrance) {
        Integer drive = freelances.findMaxDriveMinutes(freelanceId).orElse(null);
        Integer transit = freelances.findMaxTransitMinutes(freelanceId).orElse(null);
        if (drive == null && transit == null) {
            return profile;
        }
        return profile.reaching(reach(drive, transit, 1, ileDeFrance), reach(drive, transit, REACHABLE_COMMUTE_FACTOR, ileDeFrance));
    }

    private static double reach(Integer drive, Integer transit, double factor, boolean ileDeFrance) {
        double byCar = drive == null ? 0 : CommuteService.reachKm((int) Math.round(drive * factor), TravelMode.DRIVING, ileDeFrance);
        double byTransit = transit == null ? 0 :
            CommuteService.reachKm((int) Math.round(transit * factor), TravelMode.TRANSIT, ileDeFrance);
        return Math.max(byCar, byTransit);
    }

    @Autowired
    void setMentions(OfferMentionIndexer mentions) {
        this.mentions = mentions;
    }

    /** Where distances are measured from: the profile's city, or the workspace's. */
    Optional<Origin> origin(Long freelanceId) {
        return home(freelanceId).map(OfferSearchService::origin);
    }

    private static Origin origin(GeoLocator.Place place) {
        return new Origin(place.latitude(), place.longitude());
    }

    /** The place of the profile's city, or of the workspace's. */
    Optional<GeoLocator.Place> home(Long freelanceId) {
        return freelances.findProfileCity(freelanceId)
            .filter(city -> !city.isBlank())
            .or(() -> freelances.findById(freelanceId).map(Freelance::getCity).filter(city -> city != null && !city.isBlank()))
            .flatMap(city -> geoLocator.locate(city, null));
    }

    private Set<OfferSite> excludedSites(Long freelanceId) {
        return freelanceId == null ? Set.of() : Set.copyOf(exclusions.findSitesByFreelanceId(freelanceId));
    }

    private Map<Long, List<String>> skillsOf(List<Long> ids) {
        Map<Long, List<String>> skills = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : offers.findSkillsOf(ids)) {
                skills.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((String) row[1]);
            }
        }
        return skills;
    }

    /** The other offers of each offer's copy group, keyed by the offer. */
    private Map<Long, List<JobOffer>> copiesOf(List<JobOffer> found) {
        Set<Long> groups = found.stream().map(JobOffer::getCopyGroup).filter(Objects::nonNull).collect(Collectors.toSet());
        if (groups.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<JobOffer>> members = offers.findByCopyGroupIn(groups).stream().collect(Collectors.groupingBy(JobOffer::getCopyGroup));
        Map<Long, List<JobOffer>> copies = new HashMap<>();
        for (JobOffer offer : found) {
            if (offer.getCopyGroup() != null) {
                copies.put(offer.getId(), members.getOrDefault(offer.getCopyGroup(), List.of()).stream()
                    .filter(member -> !member.getId().equals(offer.getId()))
                    .toList());
            }
        }
        return copies;
    }

    /** Where else the mission is open: one copy per other site the workspace keeps, the earliest, by site name. */
    /**
     * What an offer shows beside itself: the open copies of its mission on other sites (one per site), how its
     * intermediaries' rates spread, and its history.
     */
    record Related(List<OfferCopyDto> alsoOn, OfferPublisherSpreadDto spread, OfferHistoryDto history) {

        Related with(OfferHistoryDto offerHistory) {
            return new Related(alsoOn, spread, offerHistory);
        }
    }

    static Related alsoOn(JobOffer offer, List<JobOffer> copies, Set<OfferSite> excluded, Instant now) {
        Map<OfferSite, JobOffer> bySite = new EnumMap<>(OfferSite.class);
        List<JobOffer> open = copies.stream().filter(copy -> copy.getExpiresAt() == null || copy.getExpiresAt().isAfter(now)).toList();
        open.stream()
            .filter(copy -> copy.getSite() != offer.getSite() && !excluded.contains(copy.getSite()))
            .sorted(Comparator.comparing(JobOffer::getPublishedAt))
            .forEach(copy -> bySite.putIfAbsent(copy.getSite(), copy));
        List<OfferCopyDto> alsoOn = bySite.values().stream()
            .sorted(Comparator.comparing(copy -> copy.getSite().getDisplayName()))
            .map(copy -> new OfferCopyDto(copy.getId(), copy.getSite(), copy.getSite().getDisplayName(), copy.getUrl()))
            .toList();
        return new Related(alsoOn, spread(offer, open), null);
    }

    /** The intermediaries publishing the offer's mission (it and its open copies), when more than one does. */
    static OfferPublisherSpreadDto spread(JobOffer offer, List<JobOffer> copies) {
        Map<String, Integer> bestRates = new HashMap<>();
        Set<String> publishers = new HashSet<>();
        Stream.concat(Stream.of(offer), copies.stream()).filter(item -> item.getCompanyKey() != null).forEach(item -> {
            publishers.add(item.getCompanyKey());
            Integer rate = OfferMatch.bestRate(item.getRateMin(), item.getRateMax());
            if (rate != null) {
                bestRates.merge(item.getCompanyKey(), rate, Math::max);
            }
        });
        if (publishers.size() < 2) {
            return null;
        }
        return new OfferPublisherSpreadDto(publishers.size(), bestRates.values().stream().min(Integer::compare).orElse(null),
            bestRates.values().stream().max(Integer::compare).orElse(null));
    }

    /** The workspace's state of each offer, merged with its copies' (see {@link OfferStateDto#merged}). */
    private Map<Long, OfferStateDto> statesOf(Long freelanceId, List<JobOffer> found, Map<Long, List<JobOffer>> copies) {
        if (freelanceId == null || found.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(found.stream().map(JobOffer::getId).toList());
        copies.values().forEach(group -> group.forEach(copy -> ids.add(copy.getId())));
        Map<Long, OfferStateDto> byOffer = states.findStates(freelanceId, ids).stream()
            .collect(Collectors.toMap(OfferStateDto::offerId, Function.identity()));
        Map<Long, OfferStateDto> merged = new HashMap<>();
        for (JobOffer offer : found) {
            List<OfferStateDto> group = new ArrayList<>();
            Stream.concat(Stream.of(offer), copies.getOrDefault(offer.getId(), List.of()).stream())
                .map(member -> byOffer.get(member.getId()))
                .filter(Objects::nonNull)
                .forEach(group::add);
            if (!group.isEmpty()) {
                merged.put(offer.getId(), OfferStateDto.merged(offer.getId(), group));
            }
        }
        return merged;
    }

    private OfferDto toDto(JobOffer offer, List<String> skills, OfferStateDto state, Related related, OfferMatch.Profile profile,
                           Instant now, boolean full) {
        Origin origin = profile.origin();
        Double straightKm = origin == null || offer.getLatitude() == null || offer.getLongitude() == null ? null :
            distanceKm(origin.latitude(), origin.longitude(), offer.getLatitude(), offer.getLongitude());
        Integer distance = straightKm == null ? null : (int) Math.round(straightKm);
        OfferCommuteDto commute = straightKm == null || offer.getWorkMode() == WorkMode.REMOTE ? null :
            CommuteService.offerCommute(straightKm, offer.getDepartmentCode());
        return new OfferDto(
            offer.getId(), offer.getSite(), offer.getSite().getDisplayName(), offer.getUrl(), offer.getTitle(),
            offer.getCompany(), excerpt(offer.getDescription()), full ? offer.getDescription() : null,
            offer.getRateMin(), offer.getRateMax(), offer.getCity(), offer.getPostalCode(), offer.getDepartmentCode(),
            FrenchGeography.department(offer.getDepartmentCode()).map(FrenchGeography.Department::name).orElse(null),
            FrenchGeography.region(offer.getRegionCode()).map(FrenchGeography.Region::name).orElse(null),
            offer.getCountryCode(), distance, offer.getWorkMode(), offer.getRemoteDaysPerWeek(), offer.getDurationMonths(),
            offer.getStartDate(), offer.getStartAsap(), offer.getSeniority(), skills, offer.getPublishedAt(),
            offer.getDatePrecision(), offer.getExpiresAt(), offer.getExpiresAt() != null && !offer.getExpiresAt().isAfter(now),
            state != null && state.saved(), state != null && state.hidden(), state != null && state.seen(),
            state == null ? null : state.importedProjectId(), related.alsoOn(), OfferMatch.score(offer, skills, profile),
            state == null ? null : state.note(), state == null ? null : state.application(), benchmarks.compare(offer, skills),
            OfferSuspicion.of(offer.getSuspicion()), related.spread(), related.history(), commute);
    }

    /** The description's beginning on one line, cut at a word and ended with an ellipsis. */
    static String excerpt(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String line = description.replaceAll("\\s+", " ").strip();
        if (line.length() <= EXCERPT_LENGTH) {
            return line;
        }
        int cut = line.lastIndexOf(' ', EXCERPT_LENGTH);
        return line.substring(0, cut > EXCERPT_LENGTH / 2 ? cut : EXCERPT_LENGTH).strip() + "…";
    }

    /** Great-circle distance. */
    static double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double dLatitude = Math.toRadians(latitude2 - latitude1);
        double dLongitude = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(dLatitude / 2) * Math.sin(dLatitude / 2) +
            Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2)) * Math.sin(dLongitude / 2) * Math.sin(dLongitude / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
