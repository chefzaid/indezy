package dev.swirlit.indezy.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommuteServiceTest {

    @Mock
    private FreelanceRepository freelanceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private RestTemplate restTemplate;

    private CommuteService commuteService;

    private Freelance testFreelance;
    private Client testClient;
    private Project testProject;
    private ProjectDto testProjectDto;

    @BeforeEach
    void setUp() {
        // No API key configured by default: the service must degrade gracefully without calling Google.
        commuteService = new CommuteService(freelanceRepository, projectRepository, projectMapper, restTemplate);

        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setAddress("10 rue de la Paix");
        testFreelance.setCity("Paris");

        testClient = new Client();
        testClient.setId(1L);
        testClient.setCompanyName("Test Company");
        testClient.setAddress("1 avenue des Champs-Élysées");
        testClient.setCity("Paris");

        testProject = new Project();
        testProject.setId(1L);
        testProject.setRole("Full Stack Developer");
        testProject.setClient(testClient);

        testProjectDto = new ProjectDto();
        testProjectDto.setId(1L);
        testProjectDto.setRole("Full Stack Developer");
        testProjectDto.setClientName("Test Company");
    }

    @Test
    void getProjectsSortedByCommute_WithUnknownFreelance_ShouldThrowResourceNotFoundException() {
        when(freelanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commuteService.getProjectsSortedByCommute(99L, TravelMode.DRIVING))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProjectsSortedByCommute_WhenFreelanceHasNoAddress_ShouldReturnProjectsWithoutCommute() {
        testFreelance.setAddress(null);
        testFreelance.setCity(null);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProject()).isEqualTo(testProjectDto);
        assertThat(results.get(0).getCommute()).isNull();
    }

    @Test
    void getProjectsSortedByCommute_WhenClientHasNoAddress_ShouldReturnNullCommuteForProject() {
        testClient.setAddress(null);
        testClient.setCity(null);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCommute()).isNull();
    }

    @Test
    void getProjectsSortedByCommute_WithoutApiKey_ShouldReturnCommuteWithoutDuration() {
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.TRANSIT);

        assertThat(results).hasSize(1);
        CommuteInfoDto commute = results.get(0).getCommute();
        assertThat(commute).isNotNull();
        assertThat(commute.getProjectId()).isEqualTo(1L);
        assertThat(commute.getTravelMode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(commute.getDurationInSeconds()).isNull();
        assertThat(commute.getOrigin()).isEqualTo("10 rue de la Paix, Paris");
        assertThat(commute.getDestination()).isEqualTo("1 avenue des Champs-Élysées, Paris");
    }

    @Test
    void getProjectsSortedByCommute_ShouldSortProjectsWithoutCommuteLast() {
        Project projectWithoutClient = new Project();
        projectWithoutClient.setId(2L);
        projectWithoutClient.setRole("Backend Developer");
        projectWithoutClient.setClient(null);

        ProjectDto dtoWithoutClient = new ProjectDto();
        dtoWithoutClient.setId(2L);
        dtoWithoutClient.setRole("Backend Developer");

        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(projectWithoutClient, testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);
        when(projectMapper.toDto(projectWithoutClient)).thenReturn(dtoWithoutClient);

        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING);

        // Both have no duration, so order is stable; both entries are present.
        assertThat(results).hasSize(2);
        assertThat(results).extracting(r -> r.getProject().getId()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void getCommuteForProject_WithUnknownFreelance_ShouldThrowResourceNotFoundException() {
        when(freelanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commuteService.getCommuteForProject(99L, 1L, TravelMode.DRIVING))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCommuteForProject_WithUnknownProject_ShouldThrowResourceNotFoundException() {
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commuteService.getCommuteForProject(1L, 99L, TravelMode.DRIVING))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCommuteForProject_WhenAddressesMissing_ShouldReturnEmptyCommuteInfo() {
        testFreelance.setAddress(null);
        testFreelance.setCity(null);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CommuteInfoDto result = commuteService.getCommuteForProject(1L, 1L, TravelMode.DRIVING);

        assertThat(result.getProjectId()).isEqualTo(1L);
        assertThat(result.getProjectRole()).isEqualTo("Full Stack Developer");
        assertThat(result.getClientName()).isEqualTo("Test Company");
        assertThat(result.getDurationInSeconds()).isNull();
    }

    @Test
    void getCommuteForProject_WhenClientMissing_ShouldReturnEmptyCommuteInfo() {
        testProject.setClient(null);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CommuteInfoDto result = commuteService.getCommuteForProject(1L, 1L, TravelMode.DRIVING);

        assertThat(result.getClientName()).isNull();
        assertThat(result.getDestination()).isEmpty();
        assertThat(result.getDurationInSeconds()).isNull();
    }

    @Test
    void getCommuteForProject_WithoutApiKey_ShouldReturnCommuteWithoutDuration() {
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CommuteInfoDto result = commuteService.getCommuteForProject(1L, 1L, TravelMode.DRIVING);

        assertThat(result.getProjectId()).isEqualTo(1L);
        assertThat(result.getTravelMode()).isEqualTo(TravelMode.DRIVING);
        assertThat(result.getDurationInSeconds()).isNull();
    }

    @Test
    void getCommuteForProject_WithApiKeyAndOkResponse_ShouldReturnDurationAndDistance() {
        ReflectionTestUtils.setField(commuteService, "googleMapsApiKey", "test-key");
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(json("""
            {
              "status": "OK",
              "rows": [{"elements": [{
                "status": "OK",
                "duration": {"value": 1800, "text": "30 min"},
                "distance": {"value": 15000, "text": "15 km"}
              }]}]
            }
            """));

        CommuteInfoDto result = commuteService.getCommuteForProject(1L, 1L, TravelMode.DRIVING);

        assertThat(result.getDurationInSeconds()).isEqualTo(1800);
        assertThat(result.getDurationText()).isEqualTo("30 min");
        assertThat(result.getDistanceInMeters()).isEqualTo(15000);
        assertThat(result.getDistanceText()).isEqualTo("15 km");
    }

    @Test
    void getCommuteForProject_WithFailedElementStatus_ShouldReturnCommuteWithoutDuration() {
        ReflectionTestUtils.setField(commuteService, "googleMapsApiKey", "test-key");
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(json("""
            {"status": "OK", "rows": [{"elements": [{"status": "NOT_FOUND"}]}]}
            """));

        CommuteInfoDto result = commuteService.getCommuteForProject(1L, 1L, TravelMode.TRANSIT);

        assertThat(result.getDurationInSeconds()).isNull();
        assertThat(result.getTravelMode()).isEqualTo(TravelMode.TRANSIT);
    }

    @Test
    void getCommuteForProject_WithFailedApiStatus_ShouldReturnCommuteWithoutDuration() {
        ReflectionTestUtils.setField(commuteService, "googleMapsApiKey", "test-key");
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(json("""
            {"status": "REQUEST_DENIED"}
            """));

        CommuteInfoDto result = commuteService.getCommuteForProject(1L, 1L, TravelMode.DRIVING);

        assertThat(result.getDurationInSeconds()).isNull();
    }

    @Test
    void getCommuteForProject_WhenApiCallThrows_ShouldReturnCommuteWithoutDuration() {
        ReflectionTestUtils.setField(commuteService, "googleMapsApiKey", "test-key");
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class)))
            .thenThrow(new RuntimeException("network error"));

        CommuteInfoDto result = commuteService.getCommuteForProject(1L, 1L, TravelMode.DRIVING);

        assertThat(result.getDurationInSeconds()).isNull();
    }

    private static JsonNode json(String content) {
        return new ObjectMapper().readTree(content);
    }
}
