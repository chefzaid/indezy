package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.ProjectDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.ProjectMapper;
import dev.byteworks.indezy.model.Client;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.Project;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.model.enums.WorkMode;
import dev.byteworks.indezy.repository.ClientRepository;
import dev.byteworks.indezy.repository.FreelanceRepository;
import dev.byteworks.indezy.repository.ProjectRepository;
import dev.byteworks.indezy.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private FreelanceRepository freelanceRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private ProjectService projectService;

    private Project testProject;
    private ProjectDto testProjectDto;
    private Freelance testFreelance;
    private Client testClient;

    @BeforeEach
    void setUp() {
        // Setup test freelance
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        // Setup test client
        testClient = new Client();
        testClient.setId(1L);
        testClient.setCompanyName("Test Company");
        testClient.setCity("Paris");
        testClient.setIsFinal(true);
        testClient.setFreelance(testFreelance);

        // Setup test project
        testProject = new Project();
        testProject.setId(1L);
        testProject.setRole("Full Stack Developer");
        testProject.setDescription("Test project description");
        testProject.setTechStack("Java, Spring Boot, Angular");
        testProject.setDailyRate(600);
        testProject.setWorkMode(WorkMode.HYBRID);
        testProject.setRemoteDaysPerMonth(15);
        testProject.setOnsiteDaysPerMonth(5);
        testProject.setStartDate(LocalDate.of(2024, 1, 15));
        testProject.setDurationInMonths(6);
        testProject.setDaysPerYear(220);
        testProject.setPersonalRating(4);
        testProject.setFreelance(testFreelance);
        testProject.setClient(testClient);

        // Setup test DTO
        testProjectDto = new ProjectDto();
        testProjectDto.setId(1L);
        testProjectDto.setRole("Full Stack Developer");
        testProjectDto.setDescription("Test project description");
        testProjectDto.setTechStack("Java, Spring Boot, Angular");
        testProjectDto.setDailyRate(600);
        testProjectDto.setWorkMode(WorkMode.HYBRID);
        testProjectDto.setRemoteDaysPerMonth(15);
        testProjectDto.setOnsiteDaysPerMonth(5);
        testProjectDto.setStartDate(LocalDate.of(2024, 1, 15));
        testProjectDto.setDurationInMonths(6);
        testProjectDto.setDaysPerYear(220);
        testProjectDto.setPersonalRating(4);
        testProjectDto.setFreelanceId(1L);
        testProjectDto.setClientId(1L);
    }

    @Test
    void findAll_ShouldReturnAllProjects() {
        // Given
        List<Project> projects = Arrays.asList(testProject);
        when(projectRepository.findAll()).thenReturn(projects);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        List<ProjectDto> result = projectService.findAll();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("Full Stack Developer");
        verify(projectRepository).findAll();
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void findById_WhenProjectExists_ShouldReturnProject() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.findById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRole()).isEqualTo("Full Stack Developer");
        verify(projectRepository).findById(1L);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void findById_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.findById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found with id: 1");
        
        verify(projectRepository).findById(1L);
        verify(projectMapper, never()).toDto(any());
    }

    @Test
    void findByIdWithSteps_WhenProjectExists_ShouldReturnProjectWithSteps() {
        // Given
        when(projectRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.findByIdWithSteps(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(projectRepository).findByIdWithSteps(1L);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void findByFreelanceId_ShouldReturnProjectsForFreelance() {
        // Given
        List<Project> projects = Arrays.asList(testProject);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(projects);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        List<ProjectDto> result = projectService.findByFreelanceId(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFreelanceId()).isEqualTo(1L);
        verify(projectRepository).findByFreelanceId(1L);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void findByFreelanceIdAndFilters_WithMinRate_ShouldFilterCorrectly() {
        // Given
        Project lowRateProject = new Project();
        lowRateProject.setDailyRate(400);
        lowRateProject.setFreelance(testFreelance);
        
        List<Project> allProjects = Arrays.asList(testProject, lowRateProject);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(allProjects);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        List<ProjectDto> result = projectService.findByFreelanceIdAndFilters(1L, 500, null, null, null, null);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDailyRate()).isEqualTo(600);
        verify(projectRepository).findByFreelanceId(1L);
    }

    @Test
    void create_WithValidData_ShouldCreateProject() {
        // Given
        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.create(testProjectDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo("Full Stack Developer");
        verify(projectMapper).toEntity(testProjectDto);
        verify(freelanceRepository).findById(1L);
        verify(clientRepository).findById(1L);
        verify(projectRepository).save(testProject);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void create_WithInvalidFreelanceId_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.create(testProjectDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with id: 1");
        
        verify(freelanceRepository).findById(1L);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void update_WithValidData_ShouldUpdateProject() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.update(1L, testProjectDto);

        // Then
        assertThat(result).isNotNull();
        verify(projectRepository).findById(1L);
        verify(projectMapper).updateEntity(testProjectDto, testProject);
        verify(projectRepository).save(testProject);
        verify(projectMapper).toDto(testProject);
    }

    @Test
    void delete_WhenProjectExists_ShouldDeleteProject() {
        // Given
        when(projectRepository.existsById(1L)).thenReturn(true);

        // When
        projectService.delete(1L);

        // Then
        verify(projectRepository).existsById(1L);
        verify(projectRepository).deleteById(1L);
    }

    @Test
    void delete_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.existsById(1L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> projectService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found with id: 1");
        
        verify(projectRepository).existsById(1L);
        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    void getAverageDailyRateByFreelanceId_ShouldReturnAverageRate() {
        // Given
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(575.0);

        // When
        Double result = projectService.getAverageDailyRateByFreelanceId(1L);

        // Then
        assertThat(result).isEqualTo(575.0);
        verify(projectRepository).findAverageDailyRateByFreelanceId(1L);
    }

    @Test
    void countByFreelanceId_ShouldReturnProjectCount() {
        // Given
        when(projectRepository.countByFreelanceId(1L)).thenReturn(3L);

        // When
        Long result = projectService.countByFreelanceId(1L);

        // Then
        assertThat(result).isEqualTo(3L);
        verify(projectRepository).countByFreelanceId(1L);
    }
}
