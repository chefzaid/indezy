package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.KanbanBoardDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.ProjectMapper;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private InterviewStepRepository interviewStepRepository;

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

    @Test
    void findByClientId_ShouldReturnProjectsForClient() {
        // Given
        when(projectRepository.findByClientId(1L)).thenReturn(Arrays.asList(testProject));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        List<ProjectDto> result = projectService.findByClientId(1L);

        // Then
        assertThat(result).hasSize(1);
        verify(projectRepository).findByClientId(1L);
    }

    @Test
    void findByIdWithSteps_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findByIdWithSteps(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.findByIdWithSteps(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByFreelanceIdAndFilters_WithAllFilters_ShouldApplyEachFilter() {
        // Given
        Project nonMatching = new Project();
        nonMatching.setDailyRate(800);
        nonMatching.setWorkMode(WorkMode.REMOTE);
        nonMatching.setStartDate(LocalDate.of(2023, 1, 1));
        nonMatching.setTechStack("Python");
        nonMatching.setFreelance(testFreelance);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(Arrays.asList(testProject, nonMatching));
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        List<ProjectDto> result = projectService.findByFreelanceIdAndFilters(
                1L, 500, 700, WorkMode.HYBRID, LocalDate.of(2024, 1, 1), "java");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getWorkMode()).isEqualTo(WorkMode.HYBRID);
    }

    @Test
    void create_WithMiddlemanAndSource_ShouldResolveAllRelationships() {
        // Given
        Client middleman = new Client();
        middleman.setId(2L);
        Source source = new Source();
        source.setId(3L);
        testProjectDto.setMiddlemanId(2L);
        testProjectDto.setSourceId(3L);

        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(clientRepository.findById(2L)).thenReturn(Optional.of(middleman));
        when(sourceRepository.findById(3L)).thenReturn(Optional.of(source));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.create(testProjectDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(testProject.getMiddleman()).isEqualTo(middleman);
        assertThat(testProject.getSource()).isEqualTo(source);
    }

    @Test
    void create_WithoutStatus_ShouldDefaultToIdentified() {
        // Given
        testProject.setStatus(null);
        when(projectMapper.toEntity(testProjectDto)).thenReturn(testProject);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.create(testProjectDto);

        // Then
        assertThat(testProject.getStatus()).isEqualTo(ProjectStatus.IDENTIFIED);
    }

    @Test
    void update_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.update(999L, testProjectDto))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void update_WithNewMiddlemanAndSource_ShouldReassignRelationships() {
        // Given
        Client middleman = new Client();
        middleman.setId(2L);
        Source source = new Source();
        source.setId(3L);
        testProjectDto.setMiddlemanId(2L);
        testProjectDto.setSourceId(3L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(clientRepository.findById(2L)).thenReturn(Optional.of(middleman));
        when(sourceRepository.findById(3L)).thenReturn(Optional.of(source));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.update(1L, testProjectDto);

        // Then
        assertThat(testProject.getMiddleman()).isEqualTo(middleman);
        assertThat(testProject.getSource()).isEqualTo(source);
    }

    @Test
    void update_WithoutMiddlemanAndSource_ShouldClearRelationships() {
        // Given
        Client middleman = new Client();
        middleman.setId(2L);
        Source source = new Source();
        source.setId(3L);
        testProject.setMiddleman(middleman);
        testProject.setSource(source);
        testProjectDto.setMiddlemanId(null);
        testProjectDto.setSourceId(null);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.update(1L, testProjectDto);

        // Then
        assertThat(testProject.getMiddleman()).isNull();
        assertThat(testProject.getSource()).isNull();
    }

    @Test
    void updateStatus_WhenProjectExists_ShouldUpdateStatus() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        ProjectDto result = projectService.updateStatus(1L, ProjectStatus.WON, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(testProject.getStatus()).isEqualTo(ProjectStatus.WON);
        assertThat(testProject.getLostReason()).isNull();
        verify(projectRepository).save(testProject);
    }

    @Test
    void updateStatus_WhenLost_ShouldStoreLostReason() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.updateStatus(1L, ProjectStatus.LOST, LostReason.RATE_TOO_LOW);

        // Then
        assertThat(testProject.getStatus()).isEqualTo(ProjectStatus.LOST);
        assertThat(testProject.getLostReason()).isEqualTo(LostReason.RATE_TOO_LOW);
    }

    @Test
    void updateStatus_WhenNotLost_ShouldClearLostReason() {
        // Given a project that was previously lost
        testProject.setStatus(ProjectStatus.LOST);
        testProject.setLostReason(LostReason.NO_RESPONSE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When it moves back into the pipeline
        projectService.updateStatus(1L, ProjectStatus.INTERVIEW, LostReason.NO_RESPONSE);

        // Then the stale reason is cleared
        assertThat(testProject.getStatus()).isEqualTo(ProjectStatus.INTERVIEW);
        assertThat(testProject.getLostReason()).isNull();
    }

    @Test
    void updateStatus_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.updateStatus(999L, ProjectStatus.WON, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void getKanbanBoard_ShouldGroupProjectsByStatusInColumnOrder() {
        // Given
        testProject.setStatus(ProjectStatus.APPLIED);

        Project projectWithoutStatus = new Project();
        projectWithoutStatus.setId(2L);
        projectWithoutStatus.setRole("Backend Developer");
        projectWithoutStatus.setFreelance(testFreelance);

        InterviewStep completedStep = new InterviewStep();
        completedStep.setStatus(StepStatus.VALIDATED);
        InterviewStep failedStep = new InterviewStep();
        failedStep.setStatus(StepStatus.FAILED);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(Arrays.asList(testProject, projectWithoutStatus));
        when(interviewStepRepository.findByProjectId(1L)).thenReturn(Arrays.asList(completedStep, failedStep));
        when(interviewStepRepository.findByProjectId(2L)).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L);

        // Then
        assertThat(board.getColumnOrder()).containsExactly(
                "IDENTIFIED", "APPLIED", "INTERVIEW", "OFFER", "WON", "LOST");
        assertThat(board.getColumns().get("APPLIED")).hasSize(1);
        assertThat(board.getColumns().get("IDENTIFIED")).hasSize(1); // null status defaults to IDENTIFIED

        KanbanBoardDto.ProjectCardDto card = board.getColumns().get("APPLIED").get(0);
        assertThat(card.getProjectId()).isEqualTo(1L);
        assertThat(card.getClientName()).isEqualTo("Test Company");
        assertThat(card.getTotalSteps()).isEqualTo(2);
        assertThat(card.getCompletedSteps()).isEqualTo(1);
        assertThat(card.getFailedSteps()).isEqualTo(1);
    }

    @Test
    void getKanbanBoard_ShouldPinFavoritesToTopOfColumn() {
        // Given two projects in the same column, only the second is a favorite.
        testProject.setStatus(ProjectStatus.APPLIED);
        testProject.setIsFavorite(false);

        Project favorite = new Project();
        favorite.setId(2L);
        favorite.setRole("Lead Developer");
        favorite.setStatus(ProjectStatus.APPLIED);
        favorite.setFreelance(testFreelance);
        favorite.setIsFavorite(true);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(Arrays.asList(testProject, favorite));
        when(interviewStepRepository.findByProjectId(any())).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L);

        // Then the favorite is listed first.
        List<KanbanBoardDto.ProjectCardDto> applied = board.getColumns().get("APPLIED");
        assertThat(applied).hasSize(2);
        assertThat(applied.get(0).getProjectId()).isEqualTo(2L);
        assertThat(applied.get(0).getIsFavorite()).isTrue();
        assertThat(applied.get(1).getProjectId()).isEqualTo(1L);
    }

    @Test
    void getKanbanBoard_ShouldFlagProjectsWithSameClientAndRoleAsDuplicates() {
        // Given two opportunities for the same client with the same role (different case),
        // plus a third with a different role for the same client.
        testProject.setStatus(ProjectStatus.IDENTIFIED);

        Project duplicate = new Project();
        duplicate.setId(2L);
        duplicate.setRole("full stack developer");
        duplicate.setStatus(ProjectStatus.APPLIED);
        duplicate.setFreelance(testFreelance);
        duplicate.setClient(testClient);

        Project distinct = new Project();
        distinct.setId(3L);
        distinct.setRole("DevOps Engineer");
        distinct.setStatus(ProjectStatus.IDENTIFIED);
        distinct.setFreelance(testFreelance);
        distinct.setClient(testClient);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(Arrays.asList(testProject, duplicate, distinct));
        when(interviewStepRepository.findByProjectId(any())).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L);

        // Then the matching pair is flagged, the unique role is not.
        Map<Long, Boolean> flagsById = board.getColumns().values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toMap(
                KanbanBoardDto.ProjectCardDto::getProjectId,
                KanbanBoardDto.ProjectCardDto::getIsPotentialDuplicate));
        assertThat(flagsById.get(1L)).isTrue();
        assertThat(flagsById.get(2L)).isTrue();
        assertThat(flagsById.get(3L)).isFalse();
    }

    @Test
    void getKanbanBoard_WithUniqueRoles_ShouldFlagNoDuplicates() {
        // Given a single project, nothing can be a duplicate.
        testProject.setStatus(ProjectStatus.IDENTIFIED);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(interviewStepRepository.findByProjectId(any())).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L);

        // Then
        KanbanBoardDto.ProjectCardDto card = board.getColumns().get("IDENTIFIED").get(0);
        assertThat(card.getIsPotentialDuplicate()).isFalse();
    }

    @Test
    void toggleFavorite_ShouldFlipFlagAndPersist() {
        // Given
        testProject.setIsFavorite(false);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.toggleFavorite(1L);

        // Then
        assertThat(testProject.getIsFavorite()).isTrue();
        verify(projectRepository).save(testProject);
    }

    @Test
    void toggleFavorite_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.toggleFavorite(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void getDashboardStats_ShouldAggregateCountsRatesAndRanges() {
        // Given
        when(projectRepository.countByFreelanceId(1L)).thenReturn(2L);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(600.0);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L))
                .thenReturn(List.<Object[]>of(new Object[]{ProjectStatus.WON, 1L}));
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L))
                .thenReturn(List.<Object[]>of(new Object[]{WorkMode.HYBRID, 2L}));
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));

        // When
        DashboardStatsDto stats = projectService.getDashboardStats(1L);

        // Then
        assertThat(stats.getTotalProjects()).isEqualTo(2L);
        assertThat(stats.getAverageDailyRate()).isEqualTo(600.0);
        assertThat(stats.getWonProjects()).isEqualTo(1L);
        assertThat(stats.getLostProjects()).isZero();
        assertThat(stats.getActiveProjects()).isEqualTo(1L);
        assertThat(stats.getProjectsByStatus().get("WON")).isEqualTo(1L);
        assertThat(stats.getProjectsByWorkMode().get("HYBRID")).isEqualTo(2L);
        // testProject has dailyRate 600 -> falls in the 500-700 bucket
        assertThat(stats.getDailyRateRanges())
                .filteredOn(r -> r.getLabel().equals("500-700"))
                .first()
                .satisfies(r -> assertThat(r.getCount()).isEqualTo(1L));
    }

    @Test
    void getDashboardStats_ShouldBreakDownLostReasons() {
        // Given a lost project with a reason and a non-lost project
        Project lost = new Project();
        lost.setStatus(ProjectStatus.LOST);
        lost.setLostReason(LostReason.RATE_TOO_LOW);
        when(projectRepository.countByFreelanceId(1L)).thenReturn(2L);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(600.0);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L)).thenReturn(List.of());
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L)).thenReturn(List.of());
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject, lost));

        // When
        DashboardStatsDto stats = projectService.getDashboardStats(1L);

        // Then only the lost project contributes to the breakdown
        assertThat(stats.getLostReasonsBreakdown())
                .containsEntry("RATE_TOO_LOW", 1L)
                .containsEntry("NO_RESPONSE", 0L);
    }

    @Test
    void getDashboardStats_WithNoData_ShouldReturnZeroDefaults() {
        // Given
        when(projectRepository.countByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L)).thenReturn(List.of());
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L)).thenReturn(List.of());
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of());

        // When
        DashboardStatsDto stats = projectService.getDashboardStats(1L);

        // Then
        assertThat(stats.getTotalProjects()).isZero();
        assertThat(stats.getAverageDailyRate()).isZero();
        assertThat(stats.getTotalEstimatedRevenue()).isZero();
        assertThat(stats.getWonProjects()).isZero();
        assertThat(stats.getLostProjects()).isZero();
        assertThat(stats.getActiveProjects()).isZero();
    }
}
