package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.InterviewStepDto;
import dev.byteworks.indezy.dto.KanbanBoardDto;
import dev.byteworks.indezy.dto.StepTransitionDto;
import dev.byteworks.indezy.model.enums.StepStatus;
import dev.byteworks.indezy.service.InterviewStepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewStepControllerTest {

    @Mock
    private InterviewStepService interviewStepService;

    @InjectMocks
    private InterviewStepController interviewStepController;

    private InterviewStepDto testInterviewStepDto;
    private KanbanBoardDto testKanbanBoardDto;
    private StepTransitionDto testStepTransitionDto;

    @BeforeEach
    void setUp() {
        // Setup test DTO
        testInterviewStepDto = new InterviewStepDto();
        testInterviewStepDto.setId(1L);
        testInterviewStepDto.setTitle("Technical Interview");
        testInterviewStepDto.setDate(LocalDateTime.of(2024, 1, 10, 14, 30));
        testInterviewStepDto.setStatus(StepStatus.PLANNED);
        testInterviewStepDto.setNotes("Technical interview with the team");
        testInterviewStepDto.setProjectId(1L);
        testInterviewStepDto.setProjectRole("Full Stack Developer");

        // Setup kanban board DTO
        testKanbanBoardDto = new KanbanBoardDto();
        Map<String, List<KanbanBoardDto.ProjectCardDto>> columns = new HashMap<>();
        testKanbanBoardDto.setColumns(columns);
        testKanbanBoardDto.setStepOrder(Arrays.asList("Prise de Contact", "Entretien Commercial"));

        // Setup transition DTO
        testStepTransitionDto = new StepTransitionDto();
        testStepTransitionDto.setProjectId(1L);
        testStepTransitionDto.setFromStepTitle("Technical Interview");
        testStepTransitionDto.setToStepTitle("Manager Interview");
        testStepTransitionDto.setNotes("Moving to next step");
    }

    @Test
    void getAllInterviewSteps_ShouldReturnListOfSteps() {
        // Given
        List<InterviewStepDto> steps = Arrays.asList(testInterviewStepDto);
        when(interviewStepService.findAll()).thenReturn(steps);

        // When
        ResponseEntity<List<InterviewStepDto>> response = interviewStepController.getAllInterviewSteps();

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(steps);
        verify(interviewStepService).findAll();
    }

    @Test
    void getInterviewStepById_WithExistingId_ShouldReturnStep() {
        // Given
        when(interviewStepService.findById(1L)).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.getInterviewStepById(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).findById(1L);
    }

    @Test
    void getInterviewStepsByProjectId_ShouldReturnStepsForProject() {
        // Given
        List<InterviewStepDto> steps = Arrays.asList(testInterviewStepDto);
        when(interviewStepService.findByProjectId(1L)).thenReturn(steps);

        // When
        ResponseEntity<List<InterviewStepDto>> response = interviewStepController.getInterviewStepsByProjectId(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(steps);
        verify(interviewStepService).findByProjectId(1L);
    }

    @Test
    void getInterviewStepsByProjectIdOrderByDate_ShouldReturnOrderedSteps() {
        // Given
        List<InterviewStepDto> steps = Arrays.asList(testInterviewStepDto);
        when(interviewStepService.findByProjectIdOrderByDate(1L)).thenReturn(steps);

        // When
        ResponseEntity<List<InterviewStepDto>> response = interviewStepController.getInterviewStepsByProjectIdOrderByDate(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(steps);
        verify(interviewStepService).findByProjectIdOrderByDate(1L);
    }

    @Test
    void getInterviewStepsByFreelanceIdAndStatus_WithStatus_ShouldReturnFilteredSteps() {
        // Given
        List<InterviewStepDto> steps = Arrays.asList(testInterviewStepDto);
        when(interviewStepService.findByFreelanceIdAndStatus(1L, StepStatus.PLANNED)).thenReturn(steps);

        // When
        ResponseEntity<List<InterviewStepDto>> response = interviewStepController.getInterviewStepsByFreelanceIdAndStatus(1L, StepStatus.PLANNED);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(steps);
        verify(interviewStepService).findByFreelanceIdAndStatus(1L, StepStatus.PLANNED);
    }

    @Test
    void createInterviewStep_WithValidData_ShouldCreateStep() {
        // Given
        when(interviewStepService.create(any(InterviewStepDto.class))).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.createInterviewStep(testInterviewStepDto);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).create(any(InterviewStepDto.class));
    }

    @Test
    void updateInterviewStep_WithValidData_ShouldUpdateStep() {
        // Given
        when(interviewStepService.update(eq(1L), any(InterviewStepDto.class))).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.updateInterviewStep(1L, testInterviewStepDto);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).update(eq(1L), any(InterviewStepDto.class));
    }

    @Test
    void deleteInterviewStep_WithExistingId_ShouldDeleteStep() {
        // Given
        doNothing().when(interviewStepService).delete(1L);

        // When
        ResponseEntity<Void> response = interviewStepController.deleteInterviewStep(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(interviewStepService).delete(1L);
    }

    @Test
    void updateInterviewStepStatus_ShouldUpdateStatus() {
        // Given
        when(interviewStepService.updateStatus(1L, StepStatus.VALIDATED)).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.updateInterviewStepStatus(1L, StepStatus.VALIDATED);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).updateStatus(1L, StepStatus.VALIDATED);
    }

    @Test
    void scheduleInterviewStep_ShouldScheduleStep() {
        // Given
        LocalDateTime scheduledDate = LocalDateTime.of(2024, 2, 15, 10, 0);
        when(interviewStepService.scheduleStep(eq(1L), eq(scheduledDate))).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.scheduleInterviewStep(1L, scheduledDate);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).scheduleStep(eq(1L), eq(scheduledDate));
    }

    @Test
    void markAsWaitingFeedback_ShouldUpdateStatus() {
        // Given
        when(interviewStepService.markAsWaitingFeedback(1L)).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.markAsWaitingFeedback(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).markAsWaitingFeedback(1L);
    }

    @Test
    void markAsValidated_ShouldUpdateStatus() {
        // Given
        when(interviewStepService.markAsValidated(1L)).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.markAsValidated(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).markAsValidated(1L);
    }

    @Test
    void markAsFailed_ShouldUpdateStatus() {
        // Given
        when(interviewStepService.markAsFailed(1L)).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.markAsFailed(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).markAsFailed(1L);
    }

    @Test
    void markAsCanceled_ShouldUpdateStatus() {
        // Given
        when(interviewStepService.markAsCanceled(1L)).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.markAsCanceled(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).markAsCanceled(1L);
    }

    @Test
    void getKanbanBoard_ShouldReturnBoardData() {
        // Given
        when(interviewStepService.getKanbanBoard(1L)).thenReturn(testKanbanBoardDto);

        // When
        ResponseEntity<KanbanBoardDto> response = interviewStepController.getKanbanBoard(1L);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testKanbanBoardDto);
        verify(interviewStepService).getKanbanBoard(1L);
    }

    @Test
    void transitionProjectToNextStep_ShouldTransitionProject() {
        // Given
        when(interviewStepService.transitionProjectToNextStep(any(StepTransitionDto.class))).thenReturn(testInterviewStepDto);

        // When
        ResponseEntity<InterviewStepDto> response = interviewStepController.transitionProjectToNextStep(testStepTransitionDto);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(testInterviewStepDto);
        verify(interviewStepService).transitionProjectToNextStep(any(StepTransitionDto.class));
    }
}
