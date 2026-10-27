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
import dev.swirlit.indezy.repository.ProjectRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The missions sorted by what their commute costs a month (see docs/features.md#commute-time-sorting): one trip's time
 * from home to the client, by car or public transport, from Google's Distance Matrix, times two for each day on site. A
 * remote mission costs nothing and is not looked up. Destinations are asked 25 at a time, and the answers kept a week;
 * a failed lookup is not kept, so the next request tries again. Neither addresses nor the key are ever logged.
 */
@Service
@Slf4j
public class CommuteService {

    /** Working days in a month, on site unless the mission says otherwise. */
    static final int WORKING_DAYS_PER_MONTH = 20;
    /** A hybrid mission without its days on site is taken as half on site. */
    static final int HYBRID_DAYS_PER_MONTH = 10;
    /** The Distance Matrix's limit of destinations per request. */
    static final int DESTINATIONS_PER_REQUEST = 25;
    private static final String DISTANCE_MATRIX_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";
    private static final String STATUS_FIELD = "status";
    private static final String STATUS_OK = "OK";

    /** One trip from home to a destination, or why there is none. */
    record Trip(Integer seconds, String durationText, Integer meters, String distanceText, CommuteUnavailable unavailable) {

        static Trip none(CommuteUnavailable why) {
            return new Trip(null, null, null, null, why);
        }
    }

    private record Key(String origin, String destination, TravelMode mode) {
    }

    private final FreelanceRepository freelanceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final RestTemplate restTemplate;
    private final Clock clock;
    private final ExpiringCache<Key, Trip> trips = new ExpiringCache<>(Duration.ofDays(7), 5_000);

    @Value("${google.maps.api-key:}")
    private String googleMapsApiKey;

    @Autowired
    public CommuteService(FreelanceRepository freelanceRepository, ProjectRepository projectRepository, ProjectMapper projectMapper,
                          Clock clock) {
        this(freelanceRepository, projectRepository, projectMapper, new RestTemplate(), clock);
    }

    CommuteService(FreelanceRepository freelanceRepository, ProjectRepository projectRepository, ProjectMapper projectMapper,
                   RestTemplate restTemplate, Clock clock) {
        this.freelanceRepository = freelanceRepository;
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.restTemplate = restTemplate;
        this.clock = clock;
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
                .monthlySeconds(days == 0 ? Integer.valueOf(0) : monthly(trip.seconds(), days))
                .unavailable(trip == null ? null : trip.unavailable())
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
        Map<String, Trip> found = new HashMap<>();
        if (googleMapsApiKey == null || googleMapsApiKey.isBlank()) {
            destinations.forEach(destination -> found.put(destination, Trip.none(CommuteUnavailable.NO_API_KEY)));
            return found;
        }
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
                answered.add(new Trip(element.path("duration").path("value").asInt(), element.path("duration").path("text").asString(),
                    element.path("distance").path("value").asInt(), element.path("distance").path("text").asString(), null));
            } else {
                answered.add(Trip.none("NOT_FOUND".equals(status) || "ZERO_RESULTS".equals(status) ? CommuteUnavailable.NOT_FOUND :
                    CommuteUnavailable.LOOKUP_FAILED));
            }
        }
        return answered;
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
