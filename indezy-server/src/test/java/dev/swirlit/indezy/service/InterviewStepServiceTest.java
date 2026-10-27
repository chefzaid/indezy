package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.InterviewStepMapper;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        interviewStepService.updateStatus(1L, StepStatus.VALIDATED);

        // Then
        assertThat(testInterviewStep.getStatus()).isEqualTo(StepStatus.VALIDATED);
        verify(interviewStepRepository).findById(1L);
        verify(interviewStepRepository).save(testInterviewStep);
        verify(interviewStepMapper).toDto(testInterviewStep);
    }

}
