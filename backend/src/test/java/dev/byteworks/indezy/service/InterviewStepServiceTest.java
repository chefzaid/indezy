package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.InterviewStepDto;
import dev.byteworks.indezy.dto.KanbanBoardDto;
import dev.byteworks.indezy.dto.StepTransitionDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.InterviewStepMapper;
import dev.byteworks.indezy.model.Client;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.InterviewStep;
import dev.byteworks.indezy.model.Project;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.model.enums.StepStatus;
import dev.byteworks.indezy.model.enums.WorkMode;
import dev.byteworks.indezy.repository.InterviewStepRepository;
import dev.byteworks.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewStepServiceTest {

    @Mock
    private InterviewStepRepository interviewStepRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private InterviewStepMapper interviewStepMapper;

    @InjectMocks
    private InterviewStepService interviewStepService;

    private InterviewStep testInterviewStep;
    private InterviewStepDto testInterviewStepDto;
    private Project testProject;
    private Freelance testFreelance;
    private Client testClient;

    @BeforeEach
    void setUp() {
        // Setup freelance
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        // Setup client
        testClient = new Client();
        testClient.setId(1L);
        testClient.setCompanyName("Test Company");
        testClient.setCity("Paris");
        testClient.setIsFinal(true);
        testClient.setFreelance(testFreelance);

        // Setup project
        testProject = new Project();
        testProject.setId(1L);
        testProject.setRole("Full Stack Developer");
        testProject.setDailyRate(600);
        testProject.setWorkMode(WorkMode.HYBRID);
        testProject.setStartDate(LocalDate.of(2024, 1, 15));
        testProject.setFreelance(testFreelance);
        testProject.setClient(testClient);

        // Setup interview step
        testInterviewStep = new InterviewStep();
        testInterviewStep.setId(1L);
        testInterviewStep.setTitle("Technical Interview");
        testInterviewStep.setDate(LocalDateTime.of(2024, 1, 10, 14, 30));
        testInterviewStep.setStatus(StepStatus.PLANNED);
        testInterviewStep.setNotes("Technical interview with the team");
        testInterviewStep.setProject(testProject);

        // Setup DTO
        testInterviewStepDto = new InterviewStepDto();
        testInterviewStepDto.setId(1L);
        testInterviewStepDto.setTitle("Technical Interview");
        testInterviewStepDto.setDate(LocalDateTime.of(2024, 1, 10, 14, 30));
        testInterviewStepDto.setStatus(StepStatus.PLANNED);
        testInterviewStepDto.setNotes("Technical interview with the team");
        testInterviewStepDto.setProjectId(1L);
        testInterviewStepDto.setProjectRole("Full Stack Developer");
    }

    @Test
    void findAll_ShouldReturnAllInterviewSteps() {
        // Given
        List<InterviewStep> steps = Arrays.asList(testInterviewStep);
        when(interviewStepRepository.findAll()).thenReturn(steps);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        List<InterviewStepDto> result = interviewStepService.findAll();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testInterviewStepDto);
        verify(interviewStepRepository).findAll();
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void findById_WithExistingId_ShouldReturnInterviewStep() {
        // Given
        when(interviewStepRepository.findById(1L)).thenReturn(Optional.of(testInterviewStep));
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.findById(1L);

        // Then
        assertThat(result).isEqualTo(testInterviewStepDto);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void findById_WithNonExistentId_ShouldThrowResourceNotFoundException() {
        // Given
        when(interviewStepRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> interviewStepService.findById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Interview step not found with id: 999");
        
        verify(interviewStepRepository).findById(999L);
        verify(interviewStepMapper, never()).toDto(any());
    }

    @Test
    void findByProjectId_ShouldReturnStepsForProject() {
        // Given
        List<InterviewStep> steps = Arrays.asList(testInterviewStep);
        when(interviewStepRepository.findByProjectId(1L)).thenReturn(steps);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        List<InterviewStepDto> result = interviewStepService.findByProjectId(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testInterviewStepDto);
        verify(interviewStepRepository).findByProjectId(1L);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void create_WithValidData_ShouldCreateInterviewStep() {
        // Given
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(interviewStepMapper.toEntity(testInterviewStepDto)).thenReturn(testInterviewStep);
        when(interviewStepRepository.save(testInterviewStep)).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.create(testInterviewStepDto);

        // Then
        assertThat(result).isEqualTo(testInterviewStepDto);
        verify(projectRepository).findById(1L);
        verify(interviewStepMapper).toEntity(testInterviewStepDto);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void create_WithNonExistentProject_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());
        testInterviewStepDto.setProjectId(999L);

        // When & Then
        assertThatThrownBy(() -> interviewStepService.create(testInterviewStepDto))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Project not found with id: 999");
        
        verify(projectRepository).findById(999L);
        verify(interviewStepMapper, never()).toEntity(any());
        verify(interviewStepRepository, never()).save(any());
    }

    @Test
    void update_WithValidData_ShouldUpdateInterviewStep() {
        // Given
        when(interviewStepRepository.findById(1L)).thenReturn(Optional.of(testInterviewStep));
        when(interviewStepRepository.save(testInterviewStep)).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.update(1L, testInterviewStepDto);

        // Then
        assertThat(result).isEqualTo(testInterviewStepDto);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepMapper).updateEntity(testInterviewStepDto, testInterviewStep);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void delete_WithExistingId_ShouldDeleteInterviewStep() {
        // Given
        when(interviewStepRepository.existsById(1L)).thenReturn(true);

        // When
        interviewStepService.delete(1L);

        // Then
        verify(interviewStepRepository).existsById(1L);
        verify(interviewStepRepository).deleteById(1L);
    }

    @Test
    void delete_WithNonExistentId_ShouldThrowResourceNotFoundException() {
        // Given
        when(interviewStepRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> interviewStepService.delete(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Interview step not found with id: 999");
        
        verify(interviewStepRepository).existsById(999L);
        verify(interviewStepRepository, never()).deleteById(any());
    }

    @Test
    void updateStatus_ShouldUpdateStepStatus() {
        // Given
        when(interviewStepRepository.findById(1L)).thenReturn(Optional.of(testInterviewStep));
        when(interviewStepRepository.save(testInterviewStep)).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.updateStatus(1L, StepStatus.VALIDATED);

        // Then
        assertThat(testInterviewStep.getStatus()).isEqualTo(StepStatus.VALIDATED);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void scheduleStep_ShouldSetDateAndStatusToPlanned() {
        // Given
        LocalDateTime scheduledDate = LocalDateTime.of(2024, 2, 15, 10, 0);
        when(interviewStepRepository.findById(1L)).thenReturn(Optional.of(testInterviewStep));
        when(interviewStepRepository.save(testInterviewStep)).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.scheduleStep(1L, scheduledDate);

        // Then
        assertThat(testInterviewStep.getDate()).isEqualTo(scheduledDate);
        assertThat(testInterviewStep.getStatus()).isEqualTo(StepStatus.PLANNED);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void markAsValidated_ShouldSetStatusToValidated() {
        // Given
        when(interviewStepRepository.findById(1L)).thenReturn(Optional.of(testInterviewStep));
        when(interviewStepRepository.save(testInterviewStep)).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.markAsValidated(1L);

        // Then
        assertThat(testInterviewStep.getStatus()).isEqualTo(StepStatus.VALIDATED);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void markAsFailed_ShouldSetStatusToFailed() {
        // Given
        when(interviewStepRepository.findById(1L)).thenReturn(Optional.of(testInterviewStep));
        when(interviewStepRepository.save(testInterviewStep)).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.markAsFailed(1L);

        // Then
        assertThat(testInterviewStep.getStatus()).isEqualTo(StepStatus.FAILED);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void getKanbanBoard_ShouldReturnBoardWithProjectsGroupedBySteps() {
        // Given
        List<Project> projects = Arrays.asList(testProject);
        List<InterviewStep> steps = Arrays.asList(testInterviewStep);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(projects);
        when(interviewStepRepository.findByProjectIdOrderByDate(1L)).thenReturn(steps);

        // When
        KanbanBoardDto result = interviewStepService.getKanbanBoard(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStepOrder()).containsExactly(
            "Prise de Contact", "Entretien Commercial", "Positionnement",
            "Test Technique", "Entretien Technique", "Entretien Manager", "Validation"
        );
        assertThat(result.getColumns()).isNotNull();
        verify(projectRepository).findByFreelanceId(1L);
        verify(interviewStepRepository).findByProjectIdOrderByDate(1L);
    }

    @Test
    void transitionProjectToNextStep_ShouldValidatePreviousStepAndCreateNewStep() {
        // Given
        StepTransitionDto transitionDto = new StepTransitionDto();
        transitionDto.setProjectId(1L);
        transitionDto.setFromStepTitle("Technical Interview");
        transitionDto.setToStepTitle("Manager Interview");
        transitionDto.setNotes("Moving to next step");

        List<InterviewStep> currentSteps = Arrays.asList(testInterviewStep);

        when(interviewStepRepository.findByProjectId(1L)).thenReturn(currentSteps);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(interviewStepRepository.save(any(InterviewStep.class))).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(any(InterviewStep.class))).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.transitionProjectToNextStep(transitionDto);

        // Then
        assertThat(testInterviewStep.getStatus()).isEqualTo(StepStatus.VALIDATED);
        verify(interviewStepRepository).findByProjectId(1L);
        verify(projectRepository).findById(1L);
        verify(interviewStepRepository, times(2)).save(any(InterviewStep.class));
        verify(interviewStepMapper).toDto(any(InterviewStep.class));
    }

    @Test
    void transitionProjectToNextStep_WithNonExistentFromStep_ShouldThrowResourceNotFoundException() {
        // Given
        StepTransitionDto transitionDto = new StepTransitionDto();
        transitionDto.setProjectId(1L);
        transitionDto.setFromStepTitle("Non-existent Step");
        transitionDto.setToStepTitle("Manager Interview");

        List<InterviewStep> currentSteps = Arrays.asList(testInterviewStep);

        when(interviewStepRepository.findByProjectId(1L)).thenReturn(currentSteps);

        // When & Then
        assertThatThrownBy(() -> interviewStepService.transitionProjectToNextStep(transitionDto))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Step 'Non-existent Step' not found for project 1");

        verify(interviewStepRepository).findByProjectId(1L);
        verify(projectRepository, never()).findById(any());
        verify(interviewStepRepository, never()).save(any());
    }

    @Test
    void markAsCanceled_ShouldSetStatusToCanceled() {
        // Given
        when(interviewStepRepository.findById(1L)).thenReturn(Optional.of(testInterviewStep));
        when(interviewStepRepository.save(testInterviewStep)).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.markAsCanceled(1L);

        // Then
        assertThat(testInterviewStep.getStatus()).isEqualTo(StepStatus.CANCELED);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

    @Test
    void markAsWaitingFeedback_ShouldSetStatusToWaitingFeedback() {
        // Given
        when(interviewStepRepository.findById(1L)).thenReturn(Optional.of(testInterviewStep));
        when(interviewStepRepository.save(testInterviewStep)).thenReturn(testInterviewStep);
        when(interviewStepMapper.toDto(testInterviewStep)).thenReturn(testInterviewStepDto);

        // When
        InterviewStepDto result = interviewStepService.markAsWaitingFeedback(1L);

        // Then
        assertThat(testInterviewStep.getStatus()).isEqualTo(StepStatus.WAITING_FEEDBACK);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }
}
