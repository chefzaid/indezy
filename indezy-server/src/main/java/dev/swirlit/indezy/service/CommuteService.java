package dev.swirlit.indezy.service;

import tools.jackson.databind.JsonNode;
import dev.swirlit.indezy.dto.CommuteInfoDto;
import dev.swirlit.indezy.dto.ProjectCommuteDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.ProjectMapper;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.CommuteUnavailable;
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferSpecifications;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.scraping.GeoLocator;
import dev.swirlit.indezy.util.ExpiringCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The missions sorted by what their commute costs a month (see docs/features.md#commute-time-sorting): one trip's time
 * from home to the client, by car or public transport, times two for each day on site. A remote mission costs nothing
 * and is not looked up. Without a Google Maps key (the free way, production's), the trip is estimated from the straight
 * line between the two addresses, located through the national address search. With one, it comes from Google's
 * Distance Matrix: destinations asked 25 at a time, the answers kept a week and a failed lookup not kept, so the next
 * request tries again. Neither addresses nor the key are ever logged.
 */
@Service
@Slf4j
public class CommuteService {

    /** Working days in a month, on site unless the mission says otherwise. */
    static final int WORKING_DAYS_PER_MONTH = 20;
    /** A hybrid mission without its days on site is taken as half on site. */
    static final int HYBRID_DAYS_PER_MONTH = 10;
    /** How much longer roads run than the straight line, on average. */
    static final double ROAD_FACTOR = 1.3;
    /** How much longer a trip takes at rush hour: by car touching Île-de-France, by car elsewhere, a short drive, transit. */
    static final double RUSH_ILE_DE_FRANCE = 2.2;
    static final double RUSH_CITY = 1.4;
    static final double RUSH_SHORT = 1.2;
    static final double RUSH_TRANSIT = 1.15;
    private static final Set<String> ILE_DE_FRANCE = Set.of("75", "77", "78", "91", "92", "93", "94", "95");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** The Distance Matrix's limit of destinations per request. */
    static final int DESTINATIONS_PER_REQUEST = 25;
    private static final String DISTANCE_MATRIX_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";
    private static final String STATUS_FIELD = "status";
    private static final String STATUS_OK = "OK";

    /** One trip from home to a destination, outside and at rush hour, or why there is none. */
    record Trip(Integer seconds, Integer rushSeconds, String durationText, Integer meters, String distanceText,
                CommuteUnavailable unavailable, boolean estimated) {

        static Trip none(CommuteUnavailable why) {
            return new Trip(null, null, null, null, null, why, false);
        }

        /** What a working day costs: the rush-hour time when known. */
        Integer commuteSeconds() {
            return rushSeconds != null ? rushSeconds : seconds;
        }
    }

    private record Key(String origin, String destination, TravelMode mode) {
    }

    private final FreelanceRepository freelanceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final RestTemplate restTemplate;
    private final Clock clock;
    private final GeoLocator geoLocator;
    private final ExpiringCache<Key, Trip> trips = new ExpiringCache<>(Duration.ofDays(7), 5_000);

    @Value("${google.maps.api-key:}")
    private String googleMapsApiKey;

    @Autowired
    public CommuteService(FreelanceRepository freelanceRepository, ProjectRepository projectRepository, ProjectMapper projectMapper,
                          Clock clock, GeoLocator geoLocator) {
        this(freelanceRepository, projectRepository, projectMapper, new RestTemplate(), clock, geoLocator);
    }

    CommuteService(FreelanceRepository freelanceRepository, ProjectRepository projectRepository, ProjectMapper projectMapper,
                   RestTemplate restTemplate, Clock clock, GeoLocator geoLocator) {
        this.freelanceRepository = freelanceRepository;
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.geoLocator = geoLocator;
    }

    @Transactional(readOnly = true)
    public List<ProjectCommuteDto> getProjectsSortedByCommute(Long freelanceId, TravelMode travelMode) {
        Freelance freelance = freelanceRepository.findById(freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Freelance not found: " + freelanceId));
        String origin = address(freelance.getAddress(), freelance.getCity());
        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);
        Map<String, Trip> byDestination = origin.isBlank() ? Map.of() : lookUp(origin, projects.stream()
            .filter(project -> project.getWorkMode() != WorkMode.REMOTE).map(project -> clientAddress(project.getClient()))
            .filter(destination -> !destination.isBlank()).toList(), travelMode);

        List<ProjectCommuteDto> results = new ArrayList<>();
        for (Project project : projects) {
            ProjectDto dto = projectMapper.toDto(project);
            String destination = clientAddress(project.getClient());
            int days = onSiteDays(project);
            Trip trip;
            if (days == 0) {
                trip = null;
            } else if (origin.isBlank()) {
                trip = Trip.none(CommuteUnavailable.NO_HOME_ADDRESS);
            } else if (destination.isBlank()) {
                trip = Trip.none(CommuteUnavailable.NO_CLIENT_ADDRESS);
            } else {
                trip = byDestination.get(destination);
            }
            results.add(ProjectCommuteDto.builder().project(dto).commute(CommuteInfoDto.builder()
                .projectId(project.getId()).projectRole(dto.getRole()).clientName(dto.getClientName())
                .origin(origin).destination(destination).travelMode(travelMode)
                .remote(days == 0).onsiteDaysPerMonth(days)
                .durationInSeconds(trip == null ? null : trip.seconds()).durationText(trip == null ? null : trip.durationText())
                .distanceInMeters(trip == null ? null : trip.meters()).distanceText(trip == null ? null : trip.distanceText())
                .rushDurationInSeconds(trip == null ? null : trip.rushSeconds())
                .monthlySeconds(days == 0 ? Integer.valueOf(0) : monthly(trip.commuteSeconds(), days))
                .unavailable(trip == null ? null : trip.unavailable())
                .estimated(trip != null && trip.estimated())
                .build()).build());
        }
        // The cheapest commute first, a remote mission costing nothing; those without a time last.
        results.sort(Comparator.comparing(result -> result.getCommute().getMonthlySeconds(), Comparator.nullsLast(Comparator.naturalOrder())));
        return results;
    }

    /** Days on site a month: none when remote, as the mission states them, else all of them or half when hybrid. */
    static int onSiteDays(Project project) {
        if (project.getWorkMode() == WorkMode.REMOTE) {
            return 0;
        }
        if (project.getOnsiteDaysPerMonth() != null) {
            return Math.clamp(project.getOnsiteDaysPerMonth(), 0, WORKING_DAYS_PER_MONTH);
        }
        if (project.getRemoteDaysPerMonth() != null) {
            return Math.clamp(WORKING_DAYS_PER_MONTH - (long) project.getRemoteDaysPerMonth(), 0, WORKING_DAYS_PER_MONTH);
        }
        return project.getWorkMode() == WorkMode.HYBRID ? HYBRID_DAYS_PER_MONTH : WORKING_DAYS_PER_MONTH;
    }

    private static Integer monthly(Integer seconds, int days) {
        return seconds == null ? null : seconds * 2 * days;
    }

    /** One trip to each destination, from the cache or asked of Google 25 destinations at a time. */
    private Map<String, Trip> lookUp(String origin, List<String> destinations, TravelMode mode) {
        if (googleMapsApiKey == null || googleMapsApiKey.isBlank()) {
            return estimate(origin, destinations, mode);
        }
        Map<String, Trip> found = new HashMap<>();
        Instant now = clock.instant();
        List<String> missing = new ArrayList<>();
        for (String destination : new LinkedHashSet<>(destinations)) {
            Trip cached = trips.peek(new Key(origin, destination, mode), now);
            if (cached == null) {
                missing.add(destination);
            } else {
                found.put(destination, cached);
            }
        }
        for (int from = 0; from < missing.size(); from += DESTINATIONS_PER_REQUEST) {
            List<String> batch = missing.subList(from, Math.min(missing.size(), from + DESTINATIONS_PER_REQUEST));
            List<Trip> answered = ask(origin, batch, mode);
            for (int i = 0; i < batch.size(); i++) {
                Trip trip = answered.get(i);
                found.put(batch.get(i), trip);
                if (trip.unavailable() != CommuteUnavailable.LOOKUP_FAILED) {
                    trips.get(new Key(origin, batch.get(i), mode), now, key -> trip);
                }
            }
        }
        return found;
    }

    /** Asks Google for one trip to each destination, in their order; every one failed when the request fails. */
    private List<Trip> ask(String origin, List<String> destinations, TravelMode mode) {
        List<Trip> failed = destinations.stream().map(destination -> Trip.none(CommuteUnavailable.LOOKUP_FAILED)).toList();
        JsonNode response;
        try {
            // A URI, not a template: braces in an address are sent as they are.
            URI uri = UriComponentsBuilder.fromUriString(DISTANCE_MATRIX_URL)
                .queryParam("origins", origin)
                .queryParam("destinations", String.join("|", destinations))
                .queryParam("mode", mode == TravelMode.DRIVING ? "driving" : "transit")
                // The next weekday at 8:30, Paris time: driving gets its time in traffic, transit its timetable then.
                .queryParam("departure_time", rushHour(clock.instant()).getEpochSecond())
                .queryParam("language", "fr")
                .queryParam("key", googleMapsApiKey)
                .build()
                .encode()
                .toUri();
            response = restTemplate.getForObject(uri, JsonNode.class);
        } catch (RuntimeException e) {
            // The exception's message quotes the address asked, key included: only its type is logged.
            log.warn("The Distance Matrix could not be asked: {}", e.getClass().getSimpleName());
            return failed;
        }
        if (response == null || !STATUS_OK.equals(response.path(STATUS_FIELD).asString())) {
            log.warn("The Distance Matrix answered {}", response == null ? "nothing" : response.path(STATUS_FIELD).asString());
            return failed;
        }
        JsonNode elements = response.path("rows").path(0).path("elements");
        List<Trip> answered = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            JsonNode element = elements.path(i);
            String status = element.path(STATUS_FIELD).asString();
            if (STATUS_OK.equals(status)) {
                JsonNode traffic = element.path("duration_in_traffic").path("value");
                answered.add(new Trip(element.path("duration").path("value").asInt(), traffic.isMissingNode() ? null : traffic.asInt(),
                    element.path("duration").path("text").asString(), element.path("distance").path("value").asInt(),
                    element.path("distance").path("text").asString(), null, false));
            } else {
                answered.add(Trip.none("NOT_FOUND".equals(status) || "ZERO_RESULTS".equals(status) ? CommuteUnavailable.NOT_FOUND :
                    CommuteUnavailable.LOOKUP_FAILED));
            }
        }
        return answered;
    }

    /**
     * Without a Google key: both addresses located through the national address search (free, cached for good) and the
     * trip estimated from the straight line between them, marked as an estimate.
     */
    private Map<String, Trip> estimate(String origin, List<String> destinations, TravelMode mode) {
        Map<String, Trip> found = new HashMap<>();
        if (!geoLocator.canLocate()) {
            destinations.forEach(destination -> found.put(destination, Trip.none(CommuteUnavailable.NO_API_KEY)));
            return found;
        }
        Optional<GeoLocator.Place> home = geoLocator.locateAddress(origin);
        for (String destination : new LinkedHashSet<>(destinations)) {
            Optional<GeoLocator.Place> place = home.isEmpty() ? Optional.empty() : geoLocator.locateAddress(destination);
            found.put(destination, place.map(there -> estimated(Math.sqrt(JobOfferSpecifications.squaredDistanceKm(
                new JobOfferSpecifications.Origin(home.get().latitude(), home.get().longitude()), there.latitude(), there.longitude())), mode,
                ileDeFrance(home.get()) || ileDeFrance(there)))
                .orElseGet(() -> Trip.none(CommuteUnavailable.NOT_FOUND)));
        }
        return found;
    }

    /**
     * A trip estimated from the straight line: roads run about {@value #ROAD_FACTOR} times longer; a car goes 30 km/h in
     * town, 50 on a suburban trip and 80 beyond 40 km, plus 5 minutes to park; public transport 18 km/h in town, 35 in
     * the suburbs and 70 by train beyond 60 km, plus 15 minutes of walking and waiting. At rush hour a car takes
     * {@value #RUSH_ILE_DE_FRANCE} times as long on a trip of more than 10 km touching Île-de-France (Issou to Montrouge:
     * about 50 minutes, 1 h 45 at 8:30), {@value #RUSH_CITY} times elsewhere and {@value #RUSH_SHORT} on a short trip;
     * public transport, which traffic barely slows, {@value #RUSH_TRANSIT} times (fuller trains, longer waits).
     */
    static Trip estimated(double straightKm, TravelMode mode, boolean ileDeFrance) {
        double roadKm = straightKm * ROAD_FACTOR;
        double speed;
        int extraMinutes;
        double rush;
        if (mode == TravelMode.DRIVING) {
            speed = roadKm < 10 ? 30 : roadKm < 40 ? 50 : 80;
            extraMinutes = 5;
            rush = roadKm <= 10 ? RUSH_SHORT : ileDeFrance ? RUSH_ILE_DE_FRANCE : RUSH_CITY;
        } else {
            speed = roadKm < 15 ? 18 : roadKm < 60 ? 35 : 70;
            extraMinutes = 15;
            rush = RUSH_TRANSIT;
        }
        double minutes = roadKm / speed * 60 + extraMinutes;
        return new Trip((int) Math.round(minutes * 60), (int) Math.round(minutes * rush * 60), null, (int) Math.round(roadKm * 1000), null,
            null, true);
    }

    /** Whether a place lies in Île-de-France, whose roads are the most jammed at rush hour. */
    static boolean ileDeFrance(GeoLocator.Place place) {
        return place.departmentCode() != null && ILE_DE_FRANCE.contains(place.departmentCode());
    }

    /** The next weekday at 8:30, Paris time, after {@code now}: when commutes are measured. */
    static Instant rushHour(Instant now) {
        ZonedDateTime day = now.atZone(PARIS).toLocalDate().plusDays(1).atTime(8, 30).atZone(PARIS);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day.toInstant();
    }

    private static String address(String street, String city) {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) {
            sb.append(street.trim());
        }
        if (city != null && !city.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(city.trim());
        }
        return sb.toString();
    }

    private static String clientAddress(Client client) {
        return client == null ? "" : address(client.getAddress(), client.getCity());
    }
}
