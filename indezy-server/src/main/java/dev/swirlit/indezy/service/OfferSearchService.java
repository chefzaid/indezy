package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.dto.OfferRegionDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSiteDto;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferSpecifications;
import dev.swirlit.indezy.repository.JobOfferSpecifications.Origin;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import dev.swirlit.indezy.scraping.GeoLocator;
import dev.swirlit.indezy.util.FrenchGeography;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads the offer feed. Deliberately without a transaction of its own: locating the profile's city may
 * write to the geocoding cache, and every read below is a single repository query (no lazy loading).
 */
@Service
public class OfferSearchService {

    static final int EXCERPT_LENGTH = 280;
    private static final int TOP_SKILLS = 20;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final JobOfferRepository offers;
    private final JobOfferStateRepository states;
    private final OfferSiteStatusRepository statuses;
    private final FreelanceRepository freelances;
    private final GeoLocator geoLocator;
    private final Clock clock;

    @Autowired
    public OfferSearchService(JobOfferRepository offers, JobOfferStateRepository states, OfferSiteStatusRepository statuses,
                              FreelanceRepository freelances, GeoLocator geoLocator) {
        this(offers, states, statuses, freelances, geoLocator, Clock.systemUTC());
    }

    OfferSearchService(JobOfferRepository offers, JobOfferStateRepository states, OfferSiteStatusRepository statuses,
                       FreelanceRepository freelances, GeoLocator geoLocator, Clock clock) {
        this.offers = offers;
        this.states = states;
        this.statuses = statuses;
        this.freelances = freelances;
        this.geoLocator = geoLocator;
        this.clock = clock;
    }

    /** One page of offers matching the request, for {@code freelanceId}'s workspace (or none). */
    public OfferPageDto search(Long freelanceId, OfferSearchRequest request) {
        Instant now = clock.instant();
        Origin origin = freelanceId == null ? null : origin(freelanceId).orElse(null);
        if (request.getRadiusKm() != null && origin == null) {
            throw new IllegalArgumentException(ErrorMessages.OFFER_DISTANCE_NEEDS_CITY);
        }
        Page<JobOffer> page = offers.findAll(JobOfferSpecifications.matching(request, freelanceId, origin, now),
            PageRequest.of(request.getPage(), request.getSize()));
        List<Long> ids = page.getContent().stream().map(JobOffer::getId).toList();
        Map<Long, List<String>> skills = skillsOf(ids);
        Map<Long, OfferStateDto> stateByOffer = statesOf(freelanceId, ids);
        List<OfferDto> content = page.getContent().stream()
            .map(offer -> toDto(offer, skills.get(offer.getId()), stateByOffer.get(offer.getId()), origin, now, false))
            .toList();
        return new OfferPageDto(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
            origin != null);
    }

    /** One offer with its full description. */
    public OfferDto get(Long freelanceId, Long id) {
        JobOffer offer = offers.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.OFFER_NOT_FOUND, id)));
        Origin origin = freelanceId == null ? null : origin(freelanceId).orElse(null);
        return toDto(offer, skillsOf(List.of(id)).get(id), statesOf(freelanceId, List.of(id)).get(id), origin, clock.instant(), true);
    }

    /** Every job board of the feed, with its open offers and collection health. */
    public List<OfferSiteDto> sites() {
        Map<OfferSite, OfferSiteStatus> byStatus = statuses.findAll().stream()
            .collect(Collectors.toMap(OfferSiteStatus::getSite, Function.identity()));
        Map<OfferSite, Long> open = new HashMap<>();
        for (Object[] row : offers.countOpenBySite(clock.instant())) {
            open.put((OfferSite) row[0], (Long) row[1]);
        }
        return Arrays.stream(OfferSite.values())
            .map(site -> {
                OfferSiteStatus status = byStatus.get(site);
                return new OfferSiteDto(site, site.getDisplayName(), site.getHomeUrl(), open.getOrDefault(site, 0L),
                    status == null ? null : status.getLastSuccessAt(), status != null && status.getConsecutiveFailures() > 0);
            })
            .toList();
    }

    /** The open offers' most frequent skills starting with {@code prefix}. */
    public List<OfferFacetDto> skills(String prefix) {
        String start = prefix == null ? "" : prefix.strip().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "");
        List<OfferFacetDto> facets = new ArrayList<>();
        for (Object[] row : offers.findTopSkills(clock.instant(), start + "%", PageRequest.of(0, TOP_SKILLS))) {
            facets.add(new OfferFacetDto((String) row[0], (Long) row[1]));
        }
        return facets;
    }

    /** French regions with their departments, for the location filter. */
    public List<OfferRegionDto> regions() {
        return FrenchGeography.regions().stream()
            .map(region -> new OfferRegionDto(region.code(), region.name(), FrenchGeography.departmentsOf(region.code()).stream()
                .map(department -> new OfferRegionDto.Department(department.code(), department.name()))
                .toList()))
            .toList();
    }

    /** Where distances are measured from: the profile's city, or the workspace's. */
    Optional<Origin> origin(Long freelanceId) {
        return freelances.findProfileCity(freelanceId)
            .filter(city -> !city.isBlank())
            .or(() -> freelances.findById(freelanceId).map(Freelance::getCity).filter(city -> city != null && !city.isBlank()))
            .flatMap(city -> geoLocator.locate(city, null))
            .map(place -> new Origin(place.latitude(), place.longitude()));
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

    private Map<Long, OfferStateDto> statesOf(Long freelanceId, List<Long> ids) {
        if (freelanceId == null || ids.isEmpty()) {
            return Map.of();
        }
        return states.findStates(freelanceId, ids).stream().collect(Collectors.toMap(OfferStateDto::offerId, Function.identity()));
    }

    static OfferDto toDto(JobOffer offer, List<String> skills, OfferStateDto state, Origin origin, Instant now, boolean full) {
        Integer distance = origin == null || offer.getLatitude() == null || offer.getLongitude() == null ? null :
            (int) Math.round(distanceKm(origin.latitude(), origin.longitude(), offer.getLatitude(), offer.getLongitude()));
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
            state == null ? null : state.importedProjectId());
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
