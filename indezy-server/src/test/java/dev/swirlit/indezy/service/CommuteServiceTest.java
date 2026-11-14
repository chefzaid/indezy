package dev.swirlit.indezy.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import dev.swirlit.indezy.scraping.GeoLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommuteServiceTest {

    @Mock
    private FreelanceRepository freelanceRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private GeoLocator geoLocator;

    private CommuteService commuteService;
    private Freelance freelance;
    private final List<Project> projects = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        // No API key configured by default: the service must degrade gracefully without calling Google.
        commuteService = new CommuteService(freelanceRepository, projectRepository, projectMapper, restTemplate,
            Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC), geoLocator);
        freelance = new Freelance();
        freelance.setId(1L);
        freelance.setAddress("10 rue de la Paix");
        freelance.setCity("Paris");
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(freelance));
        when(projectRepository.findByFreelanceId(1L)).thenReturn(projects);
        when(projectMapper.toDto(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            ProjectDto dto = new ProjectDto();
            dto.setId(project.getId());
            dto.setRole(project.getRole());
            dto.setClientName(project.getClient() == null ? null : project.getClient().getCompanyName());
            return dto;
        });
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(CommuteService.class)).addAppender(logs);
    }

    @AfterEach
    void detach() {
        ((Logger) LoggerFactory.getLogger(CommuteService.class)).detachAppender(logs);
    }

    private Project project(long id, String city, WorkMode mode) {
        Client client = new Client();
        client.setCompanyName("Client " + id);
        client.setCity(city);
        Project project = new Project();
        project.setId(id);
        project.setRole("Role " + id);
        project.setClient(city == null ? null : client);
        project.setWorkMode(mode);
        projects.add(project);
        return project;
    }

    private static JsonNode json(String content) {
        return new ObjectMapper().readTree(content);
    }

    private static String element(int seconds) {
        return "{\"status\": \"OK\", \"duration\": {\"value\": " + seconds + ", \"text\": \"" + seconds / 60 + " min\"}, " +
            "\"distance\": {\"value\": " + seconds * 10 + ", \"text\": \"" + seconds / 100 + " km\"}}";
    }

    private CommuteInfoDto commuteOf(List<ProjectCommuteDto> results, long id) {
        return results.stream().filter(result -> result.getProject().getId() == id).findFirst().orElseThrow().getCommute();
    }

    @Test
    void refusesAnUnknownFreelance() {
        when(freelanceRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> commuteService.getProjectsSortedByCommute(99L, TravelMode.DRIVING))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void countsTheDaysOnSiteByWorkMode() {
        Project project = new Project();
        assertThat(CommuteService.onSiteDays(project)).isEqualTo(20);
        project.setWorkMode(WorkMode.HYBRID);
        assertThat(CommuteService.onSiteDays(project)).isEqualTo(10);
        project.setRemoteDaysPerMonth(12);
        assertThat(CommuteService.onSiteDays(project)).isEqualTo(8);
        project.setRemoteDaysPerMonth(30);
        assertThat(CommuteService.onSiteDays(project)).isZero();
        project.setOnsiteDaysPerMonth(4);
        assertThat(CommuteService.onSiteDays(project)).isEqualTo(4);
        project.setOnsiteDaysPerMonth(31);
        assertThat(CommuteService.onSiteDays(project)).isEqualTo(20);
        project.setWorkMode(WorkMode.REMOTE);
        assertThat(CommuteService.onSiteDays(project)).isZero();
    }

    @Test
    void saysWhyThereIsNoTime() {
        project(1, "Lyon", WorkMode.ONSITE);
        project(2, null, WorkMode.ONSITE);
        project(3, "Lille", WorkMode.REMOTE);

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.TRANSIT);
        assertThat(commuteOf(results, 1).getUnavailable()).isEqualTo(CommuteUnavailable.NO_API_KEY);
        assertThat(commuteOf(results, 1).getOrigin()).isEqualTo("10 rue de la Paix, Paris");
        assertThat(commuteOf(results, 1).getTravelMode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(commuteOf(results, 2).getUnavailable()).isEqualTo(CommuteUnavailable.NO_CLIENT_ADDRESS);
        // A remote mission costs nothing a month, so it comes first, its trip asked all the same.
        assertThat(results.get(0).getProject().getId()).isEqualTo(3L);
        assertThat(commuteOf(results, 3)).satisfies(remote -> {
            assertThat(remote.isRemote()).isTrue();
            assertThat(remote.getMonthlySeconds()).isZero();
            assertThat(remote.getUnavailable()).isEqualTo(CommuteUnavailable.NO_API_KEY);
        });

        freelance.setAddress(" ");
        freelance.setCity(null);
        results = commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);
        assertThat(commuteOf(results, 1).getUnavailable()).isEqualTo(CommuteUnavailable.NO_HOME_ADDRESS);
        assertThat(commuteOf(results, 3).isRemote()).isTrue();
        assertThat(commuteOf(results, 3).getUnavailable()).isEqualTo(CommuteUnavailable.NO_HOME_ADDRESS);
        verify(restTemplate, never()).getForObject(any(URI.class), eq(JsonNode.class));
    }

    @Test
    void sortsByTheMonthsCommuteAskingOnceAndKeepingTheAnswers() {
        ReflectionTestUtils.setField(commuteService, "googleMapsApiKey", "secret-key");
        project(1, "Lyon", WorkMode.ONSITE);
        project(2, "Nanterre {La Défense}", WorkMode.HYBRID).setOnsiteDaysPerMonth(4);
        project(3, "Lyon", WorkMode.HYBRID);
        when(restTemplate.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(json(
            "{\"status\": \"OK\", \"rows\": [{\"elements\": [" + element(1800) + ", " + element(3600) + "]}]}"));

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);

        // 1 h each way 4 days a month is less than 30 min each way 10 or 20 days.
        assertThat(results).extracting(result -> result.getProject().getId()).containsExactly(2L, 3L, 1L);
        assertThat(commuteOf(results, 1)).satisfies(commute -> {
            assertThat(commute.getDurationInSeconds()).isEqualTo(1800);
            assertThat(commute.getDurationText()).isEqualTo("30 min");
            assertThat(commute.getDistanceInMeters()).isEqualTo(18000);
            assertThat(commute.getOnsiteDaysPerMonth()).isEqualTo(20);
            assertThat(commute.getMonthlySeconds()).isEqualTo(72000);
        });
        assertThat(commuteOf(results, 2).getMonthlySeconds()).isEqualTo(28800);
        assertThat(commuteOf(results, 3).getMonthlySeconds()).isEqualTo(36000);
        ArgumentCaptor<URI> asked = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(asked.capture(), eq(JsonNode.class));
        assertThat(asked.getValue().getRawQuery()).contains("departure_time=1793172600", "origins=10%20rue%20de%20la%20Paix,%20Paris",
            "destinations=Lyon%7CNanterre%20%7BLa%20D%C3%A9fense%7D", "mode=driving", "key=secret-key");

        // Asked again: the answers kept are used; another mode is asked anew.
        commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);
        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(JsonNode.class));
        commuteService.getProjectsSortedByCommute(1L, TravelMode.TRANSIT);
        verify(restTemplate, times(2)).getForObject(any(URI.class), eq(JsonNode.class));
    }

    @Test
    void asksTwentyFiveDestinationsAtATime() {
        ReflectionTestUtils.setField(commuteService, "googleMapsApiKey", "secret-key");
        IntStream.rangeClosed(1, 30).forEach(id -> project(id, "City " + id, WorkMode.ONSITE));
        when(restTemplate.getForObject(any(URI.class), eq(JsonNode.class)))
            .thenReturn(json("{\"status\": \"OK\", \"rows\": [{\"elements\": [" +
                String.join(", ", IntStream.range(0, 25).mapToObj(i -> element(600 + i)).toList()) + "]}]}"))
            .thenReturn(json("{\"status\": \"OK\", \"rows\": [{\"elements\": [" +
                String.join(", ", IntStream.range(0, 5).mapToObj(i -> element(60 + i)).toList()) + "]}]}"));

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);

        verify(restTemplate, times(2)).getForObject(any(URI.class), eq(JsonNode.class));
        assertThat(results.get(0).getProject().getId()).isEqualTo(26L);
        assertThat(commuteOf(results, 30).getDurationInSeconds()).isEqualTo(64);
    }

    @Test
    void keepsNoFailureAndLogsNeitherAddressNorKey() {
        ReflectionTestUtils.setField(commuteService, "googleMapsApiKey", "secret-key");
        project(1, "Lyon", WorkMode.ONSITE);
        project(2, "Nowhere", WorkMode.ONSITE);
        when(restTemplate.getForObject(any(URI.class), eq(JsonNode.class)))
            .thenThrow(new RestClientException("I/O error on GET request for \"https://maps.googleapis.com/?origins=10 rue de la Paix&key=secret-key\""))
            .thenReturn(json("{\"status\": \"REQUEST_DENIED\"}"))
            .thenReturn(json("{\"status\": \"OK\", \"rows\": [{\"elements\": [" + element(900) + ", {\"status\": \"ZERO_RESULTS\"}]}]}"))
            .thenReturn(json("{\"status\": \"OK\", \"rows\": [{\"elements\": [{\"status\": \"MAX_ROUTE_LENGTH_EXCEEDED\"}]}]}"));

        assertThat(commuteOf(commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING), 1).getUnavailable())
            .isEqualTo(CommuteUnavailable.LOOKUP_FAILED);
        assertThat(commuteOf(commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING), 1).getUnavailable())
            .isEqualTo(CommuteUnavailable.LOOKUP_FAILED);
        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);
        assertThat(commuteOf(results, 1).getDurationInSeconds()).isEqualTo(900);
        assertThat(commuteOf(results, 2).getUnavailable()).isEqualTo(CommuteUnavailable.NOT_FOUND);
        assertThat(commuteOf(results, 2).getMonthlySeconds()).isNull();
        // Both answers are kept: nothing more is asked.
        commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);
        verify(restTemplate, times(3)).getForObject(any(URI.class), eq(JsonNode.class));
        // An element Google could not answer is not kept either.
        project(3, "Far", WorkMode.ONSITE);
        assertThat(commuteOf(commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING), 3).getUnavailable())
            .isEqualTo(CommuteUnavailable.LOOKUP_FAILED);

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
            .contains("The Distance Matrix could not be asked: RestClientException", "The Distance Matrix answered REQUEST_DENIED")
            .noneMatch(line -> line.contains("secret-key") || line.contains("rue de la Paix") || line.contains("Lyon"));
        assertThat(logs.list).allMatch(event -> event.getThrowableProxy() == null);
    }

    @Test
    void estimatesTheTripFromTheAddressesWithoutAKey() {
        when(geoLocator.canLocate()).thenReturn(true);
        when(geoLocator.locateAddress("10 rue de la Paix, Paris")).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", null, 48.8690, 2.3310)));
        // La Défense, about 7.5 km away; Lyon, about 390 km.
        when(geoLocator.locateAddress("La Défense")).thenReturn(Optional.of(new GeoLocator.Place("Puteaux", "92", null, 48.8920, 2.2380)));
        when(geoLocator.locateAddress("Lyon")).thenReturn(Optional.of(new GeoLocator.Place("Lyon", "69", null, 45.7640, 4.8357)));
        project(1, "La Défense", WorkMode.ONSITE);
        project(2, "Lyon", WorkMode.HYBRID).setOnsiteDaysPerMonth(2);
        project(3, "Nowhere", WorkMode.ONSITE);

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.TRANSIT);

        CommuteInfoDto defense = commuteOf(results, 1);
        assertThat(defense.isEstimated()).isTrue();
        assertThat(defense.getDurationText()).isNull();
        assertThat(defense.getDistanceInMeters()).isBetween(9000, 11000);
        // About 9.7 km of road at 18 km/h, plus a quarter of an hour: some 47 minutes.
        assertThat(defense.getDurationInSeconds()).isBetween(45 * 60, 50 * 60);
        // Inside Île-de-France but under 10 km of road: a short drive, yet by transit only a little longer at rush hour.
        assertThat(defense.getRushDurationInSeconds()).isEqualTo((int) Math.round(defense.getDurationInSeconds() * 1.15));
        assertThat(defense.getMonthlySeconds()).isEqualTo(defense.getRushDurationInSeconds() * 2 * 20);
        // About 500 km of track, mostly by high-speed rail.
        assertThat(commuteOf(results, 2).getDurationInSeconds()).isBetween(4 * 3600, 5 * 3600);
        assertThat(commuteOf(results, 3).getUnavailable()).isEqualTo(CommuteUnavailable.NOT_FOUND);
        assertThat(commuteOf(results, 3).isEstimated()).isFalse();
        verify(restTemplate, never()).getForObject(any(URI.class), eq(JsonNode.class));

        // A home address the search cannot find: no estimate for anyone.
        when(geoLocator.locateAddress("10 rue de la Paix, Paris")).thenReturn(Optional.empty());
        assertThat(commuteOf(commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING), 1).getUnavailable())
            .isEqualTo(CommuteUnavailable.NOT_FOUND);
    }

    @Test
    void givesOneOpportunitysTripByCarThenByTransit() {
        when(geoLocator.canLocate()).thenReturn(true);
        when(geoLocator.locateAddress("10 rue de la Paix, Paris")).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", null, 48.8690, 2.3310)));
        when(geoLocator.locateAddress("La Défense")).thenReturn(Optional.of(new GeoLocator.Place("Puteaux", "92", null, 48.8920, 2.2380)));
        Project defense = project(1, "La Défense", WorkMode.HYBRID);
        defense.setFreelance(freelance);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(defense));

        List<CommuteInfoDto> commutes = commuteService.getProjectCommute(1L);

        assertThat(commutes).extracting(CommuteInfoDto::getTravelMode).containsExactly(TravelMode.DRIVING, TravelMode.TRANSIT);
        assertThat(commutes).allSatisfy(commute -> {
            assertThat(commute.isEstimated()).isTrue();
            assertThat(commute.getOnsiteDaysPerMonth()).isEqualTo(10);
            assertThat(commute.getMonthlySeconds()).isEqualTo(commute.getRushDurationInSeconds() * 2 * 10);
        });
        assertThat(commutes.get(1).getDurationInSeconds()).isGreaterThan(commutes.get(0).getDurationInSeconds());
    }

    @Test
    void showsARemoteOpportunitysTripWithoutCountingADayThere() {
        when(geoLocator.canLocate()).thenReturn(true);
        when(geoLocator.locateAddress("10 rue de la Paix, Paris")).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", null, 48.8690, 2.3310)));
        when(geoLocator.locateAddress("Lyon")).thenReturn(Optional.of(new GeoLocator.Place("Lyon", "69", null, 45.7640, 4.8357)));
        when(freelanceRepository.findMaxDriveMinutes(1L)).thenReturn(Optional.of(15));
        when(freelanceRepository.findKmRate(1L)).thenReturn(Optional.of(java.math.BigDecimal.ONE));
        Project remote = project(2, "Lyon", WorkMode.REMOTE);
        remote.setFreelance(freelance);
        when(projectRepository.findById(2L)).thenReturn(Optional.of(remote));

        assertThat(commuteService.getProjectCommute(2L)).hasSize(2).allSatisfy(commute -> {
            assertThat(commute.isRemote()).isTrue();
            // About 390 km as the crow flies: the trip and its distance are shown, but no day there is counted or costed.
            assertThat(commute.getDistanceInMeters()).isGreaterThan(400_000);
            assertThat(commute.getRushDurationInSeconds()).isGreaterThan(commute.getDurationInSeconds());
            assertThat(commute.getMonthlySeconds()).isZero();
            assertThat(commute.getBeyondMaximum()).isNull();
            assertThat(commute.getDailyCost()).isNull();
        });
    }

    @Test
    void looksNothingUpWithoutAHomeAddress() {
        Project onSite = project(3, "Lyon", WorkMode.ONSITE);
        Freelance homeless = new Freelance();
        onSite.setFreelance(homeless);
        when(projectRepository.findById(3L)).thenReturn(Optional.of(onSite));
        assertThat(commuteService.getProjectCommute(3L))
            .allSatisfy(commute -> assertThat(commute.getUnavailable()).isEqualTo(CommuteUnavailable.NO_HOME_ADDRESS));
        verify(geoLocator, never()).locateAddress(any());
        assertThatThrownBy(() -> commuteService.getProjectCommute(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void goesToTheMissionsPlaceOfWorkRatherThanItsClient() {
        when(geoLocator.canLocate()).thenReturn(true);
        when(geoLocator.locateAddress("10 rue de la Paix, Paris")).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", null, 48.8690, 2.3310)));
        when(geoLocator.locateAddress("92400 Courbevoie")).thenReturn(Optional.of(new GeoLocator.Place("Courbevoie", "92", null, 48.8976, 2.2574)));
        project(1, "Lyon", WorkMode.ONSITE).setWorkAddress(" 92400 Courbevoie ");
        project(2, null, WorkMode.ONSITE).setWorkAddress("92400 Courbevoie");

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);

        assertThat(commuteOf(results, 1).getDestination()).isEqualTo("92400 Courbevoie");
        assertThat(commuteOf(results, 1).getDurationInSeconds()).isBetween(10 * 60, 30 * 60);
        assertThat(commuteOf(results, 2).getUnavailable()).isNull();
        verify(geoLocator, never()).locateAddress("Lyon");
    }

    @Test
    void costsADayOnSiteFromTheProfilesRateOrPass() {
        when(geoLocator.canLocate()).thenReturn(true);
        when(geoLocator.locateAddress("10 rue de la Paix, Paris")).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", null, 48.8690, 2.3310)));
        when(geoLocator.locateAddress("La Défense")).thenReturn(Optional.of(new GeoLocator.Place("Puteaux", "92", null, 48.8920, 2.2380)));
        Project defense = project(1, "La Défense", WorkMode.HYBRID);
        defense.setFreelance(freelance);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(defense));
        when(freelanceRepository.findKmRate(1L)).thenReturn(Optional.of(new java.math.BigDecimal("0.60")));
        when(freelanceRepository.findTransitPassMonthly(1L)).thenReturn(Optional.of(new java.math.BigDecimal("88.80")));

        List<CommuteInfoDto> commutes = commuteService.getProjectCommute(1L);

        // By car, the round trip's kilometres at 0.60 €; by transit, the pass over the 10 days on site.
        CommuteInfoDto car = commutes.get(0);
        assertThat(car.getDailyCost()).isEqualByComparingTo(java.math.BigDecimal.valueOf(car.getDistanceInMeters() * 2 * 0.6 / 1000)
            .setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(commutes.get(1).getDailyCost()).isEqualByComparingTo("8.88");

        when(freelanceRepository.findKmRate(1L)).thenReturn(Optional.empty());
        assertThat(commuteService.getProjectCommute(1L).get(0).getDailyCost()).isNull();
        assertThat(CommuteService.dailyCost(CommuteInfoDto.builder().remote(true).build(), Optional.of(java.math.BigDecimal.ONE))).isNull();
        assertThat(CommuteService.dailyCost(CommuteInfoDto.builder().travelMode(TravelMode.DRIVING).durationInSeconds(600)
            .onsiteDaysPerMonth(10).build(), Optional.of(java.math.BigDecimal.ONE))).isNull();
        assertThat(CommuteService.dailyCost(CommuteInfoDto.builder().travelMode(TravelMode.TRANSIT).durationInSeconds(600)
            .onsiteDaysPerMonth(0).build(), Optional.of(java.math.BigDecimal.ONE))).isNull();
    }

    @Test
    void flagsATripLongerThanTheProfilesLongest() {
        when(geoLocator.canLocate()).thenReturn(true);
        when(geoLocator.locateAddress("10 rue de la Paix, Paris")).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", null, 48.8690, 2.3310)));
        when(geoLocator.locateAddress("La Défense")).thenReturn(Optional.of(new GeoLocator.Place("Puteaux", "92", null, 48.8920, 2.2380)));
        project(1, "La Défense", WorkMode.ONSITE).setFreelance(freelance);
        project(2, null, WorkMode.ONSITE).setFreelance(freelance);
        when(freelanceRepository.findMaxDriveMinutes(1L)).thenReturn(Optional.of(15));
        when(freelanceRepository.findMaxTransitMinutes(1L)).thenReturn(Optional.of(90));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projects.get(0)));

        List<CommuteInfoDto> commutes = commuteService.getProjectCommute(1L);
        assertThat(commutes.get(0).getBeyondMaximum()).isTrue();
        assertThat(commutes.get(1).getBeyondMaximum()).isFalse();
        assertThat(commuteOf(commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING), 2).getBeyondMaximum()).isNull();
        when(freelanceRepository.findMaxDriveMinutes(1L)).thenReturn(Optional.empty());
        assertThat(commuteOf(commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING), 1).getBeyondMaximum()).isNull();
    }

    @Test
    void countsGooglesTimeInTrafficForTheMonth() {
        ReflectionTestUtils.setField(commuteService, "googleMapsApiKey", "secret-key");
        project(1, "Montrouge", WorkMode.ONSITE);
        when(restTemplate.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(json("{\"status\": \"OK\", \"rows\": [{\"elements\": [" +
            element(3000).replace("}}", "}, \"duration_in_traffic\": {\"value\": 6300, \"text\": \"1 h 45\"}}") + "]}]}"));

        CommuteInfoDto commute = commuteOf(commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING), 1);

        assertThat(commute.getDurationInSeconds()).isEqualTo(3000);
        assertThat(commute.getRushDurationInSeconds()).isEqualTo(6300);
        assertThat(commute.getMonthlySeconds()).isEqualTo(6300 * 2 * 20);
        assertThat(commute.isEstimated()).isFalse();
    }
}
