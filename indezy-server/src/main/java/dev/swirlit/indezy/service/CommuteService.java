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
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class CommuteService {

    private final FreelanceRepository freelanceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final RestTemplate restTemplate;

    @Value("${google.maps.api-key:}")
    private String googleMapsApiKey;

    private static final String DISTANCE_MATRIX_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";
    private static final String STATUS_FIELD = "status";
    private static final String STATUS_OK = "OK";

    @Autowired
    public CommuteService(FreelanceRepository freelanceRepository,
                          ProjectRepository projectRepository,
                          ProjectMapper projectMapper) {
        this(freelanceRepository, projectRepository, projectMapper, new RestTemplate());
    }

    CommuteService(FreelanceRepository freelanceRepository,
                   ProjectRepository projectRepository,
                   ProjectMapper projectMapper,
                   RestTemplate restTemplate) {
        this.freelanceRepository = freelanceRepository;
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.restTemplate = restTemplate;
    }

    @Transactional(readOnly = true)
    public List<ProjectCommuteDto> getProjectsSortedByCommute(Long freelanceId, TravelMode travelMode) {
        log.debug("Getting projects sorted by commute for freelance {} with mode {}", freelanceId, travelMode);

        Freelance freelance = freelanceRepository.findById(freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Freelance not found: " + freelanceId));

        String origin = buildAddress(freelance.getAddress(), freelance.getCity());
        if (origin.isBlank()) {
            log.warn("Freelance {} has no address configured", freelanceId);
            return buildResultsWithoutCommute(freelanceId);
        }

        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);
        List<ProjectCommuteDto> results = new ArrayList<>();

        for (Project project : projects) {
            ProjectDto projectDto = projectMapper.toDto(project);
            String destination = buildClientAddress(project.getClient());

            if (destination.isBlank()) {
                results.add(ProjectCommuteDto.builder()
                    .project(projectDto)
                    .commute(null)
                    .build());
                continue;
            }

            CommuteInfoDto commuteInfo = fetchCommuteInfo(
                project.getId(), projectDto.getRole(), projectDto.getClientName(),
                origin, destination, travelMode);
            results.add(ProjectCommuteDto.builder()
                .project(projectDto)
                .commute(commuteInfo)
                .build());
        }

        // Sort: projects with commute info first (by duration), then projects without
        results.sort(Comparator.comparingInt(r -> {
            if (r.getCommute() == null || r.getCommute().getDurationInSeconds() == null) {
                return Integer.MAX_VALUE;
            }
            return r.getCommute().getDurationInSeconds();
        }));

        return results;
    }

    @Transactional(readOnly = true)
    public CommuteInfoDto getCommuteForProject(Long freelanceId, Long projectId, TravelMode travelMode) {
        log.debug("Getting commute info for project {} of freelance {} with mode {}", projectId, freelanceId, travelMode);

        Freelance freelance = freelanceRepository.findById(freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Freelance not found: " + freelanceId));

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        String origin = buildAddress(freelance.getAddress(), freelance.getCity());
        String destination = buildClientAddress(project.getClient());

        if (origin.isBlank() || destination.isBlank()) {
            return CommuteInfoDto.builder()
                .projectId(projectId)
                .projectRole(project.getRole())
                .clientName(project.getClient() != null ? project.getClient().getCompanyName() : null)
                .origin(origin)
                .destination(destination)
                .travelMode(travelMode)
                .build();
        }

        return fetchCommuteInfo(projectId, project.getRole(),
            project.getClient() != null ? project.getClient().getCompanyName() : null,
            origin, destination, travelMode);
    }

    private CommuteInfoDto fetchCommuteInfo(Long projectId, String projectRole, String clientName,
                                            String origin, String destination, TravelMode travelMode) {
        if (googleMapsApiKey == null || googleMapsApiKey.isBlank()) {
            log.warn("Google Maps API key is not configured, returning empty commute info");
            return CommuteInfoDto.builder()
                .projectId(projectId)
                .projectRole(projectRole)
                .clientName(clientName)
                .origin(origin)
                .destination(destination)
                .travelMode(travelMode)
                .build();
        }

        try {
            String googleTravelMode = travelMode == TravelMode.DRIVING ? "driving" : "transit";

            String url = UriComponentsBuilder.fromUriString(DISTANCE_MATRIX_URL)
                .queryParam("origins", origin)
                .queryParam("destinations", destination)
                .queryParam("mode", googleTravelMode)
                .queryParam("language", "fr")
                .queryParam("key", googleMapsApiKey)
                .toUriString();

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && STATUS_OK.equals(response.path(STATUS_FIELD).asString())) {
                JsonNode element = response.path("rows").path(0).path("elements").path(0);
                String elementStatus = element.path(STATUS_FIELD).asString();

                if (STATUS_OK.equals(elementStatus)) {
                    return CommuteInfoDto.builder()
                        .projectId(projectId)
                        .projectRole(projectRole)
                        .clientName(clientName)
                        .origin(origin)
                        .destination(destination)
                        .travelMode(travelMode)
                        .durationInSeconds(element.path("duration").path("value").asInt())
                        .durationText(element.path("duration").path("text").asString())
                        .distanceInMeters(element.path("distance").path("value").asInt())
                        .distanceText(element.path("distance").path("text").asString())
                        .build();
                } else {
                    log.warn("Distance Matrix API returned element status: {} for origin={}, destination={}",
                        elementStatus, origin, destination);
                }
            } else {
                log.warn("Distance Matrix API returned status: {}",
                    response != null ? response.path(STATUS_FIELD).asString() : "null response");
            }
        } catch (RuntimeException e) {
            // Any failure fetching commute data (HTTP, parsing, ...) degrades gracefully
            // to commute info without duration rather than failing the whole request.
            log.error("Error calling Google Maps Distance Matrix API", e);
        }

        return CommuteInfoDto.builder()
            .projectId(projectId)
            .projectRole(projectRole)
            .clientName(clientName)
            .origin(origin)
            .destination(destination)
            .travelMode(travelMode)
            .build();
    }

    private List<ProjectCommuteDto> buildResultsWithoutCommute(Long freelanceId) {
        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);
        List<ProjectCommuteDto> results = new ArrayList<>();
        for (Project project : projects) {
            results.add(ProjectCommuteDto.builder()
                .project(projectMapper.toDto(project))
                .commute(null)
                .build());
        }
        return results;
    }

    private String buildAddress(String address, String city) {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.isBlank()) {
            sb.append(address.trim());
        }
        if (city != null && !city.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(city.trim());
        }
        return sb.toString();
    }

    private String buildClientAddress(Client client) {
        if (client == null) {
            return "";
        }
        return buildAddress(client.getAddress(), client.getCity());
    }
}
