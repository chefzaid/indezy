package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.InterviewStepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewStepControllerTest {

    @Mock
    private InterviewStepService interviewStepService;

    @Mock
    private AccessGuard accessGuard;

    @InjectMocks
    private InterviewStepController interviewStepController;

    private InterviewStepDto step;

    @BeforeEach
    void setUp() {
        step = new InterviewStepDto();
        step.setId(1L);
        step.setTitle("Technical Interview");
        step.setDate(LocalDateTime.of(2024, 1, 10, 14, 30));
        step.setStatus(StepStatus.PLANNED);
        step.setProjectId(1L);
    }

    @Test
    void getAllInterviewSteps_ShouldListTheCallersSteps() {
        when(accessGuard.currentFreelanceId()).thenReturn(Optional.of(7L));
        when(interviewStepService.findByFreelanceIdAndStatus(7L, null)).thenReturn(List.of(step));

        assertThat(interviewStepController.getAllInterviewSteps()).containsExactly(step);
    }

    @Test
    void getAllInterviewSteps_WithoutAccount_ShouldListEverySteps() {
        when(accessGuard.currentFreelanceId()).thenReturn(Optional.empty());
        when(interviewStepService.findAll()).thenReturn(List.of(step));

        assertThat(interviewStepController.getAllInterviewSteps()).containsExactly(step);
    }

    @Test
    void readEndpoints_ShouldCheckOwnershipAndDelegate() {
        when(interviewStepService.findById(1L)).thenReturn(step);
        when(interviewStepService.findByProjectIdOrderByDate(1L)).thenReturn(List.of(step));
        when(interviewStepService.findByFreelanceIdAndStatus(1L, StepStatus.PLANNED)).thenReturn(List.of(step));

        assertThat(interviewStepController.getInterviewStepById(1L)).isEqualTo(step);
        assertThat(interviewStepController.getInterviewStepsByProjectIdOrderByDate(1L)).containsExactly(step);
        assertThat(interviewStepController.getInterviewStepsByFreelanceIdAndStatus(1L, StepStatus.PLANNED))
            .containsExactly(step);
        verify(accessGuard).requireInterviewStep(1L);
        verify(accessGuard).requireProject(1L);
        verify(accessGuard).requireFreelance(1L);
    }

    @Test
    void createInterviewStep_ShouldCheckTheProjectAndReturnCreated() {
        when(interviewStepService.create(any(InterviewStepDto.class))).thenReturn(step);

        ResponseEntity<InterviewStepDto> response = interviewStepController.createInterviewStep(step);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(step);
        verify(accessGuard).requireProject(1L);
    }

    @Test
    void updateAndStatusChange_ShouldCheckTheStepAndDelegate() {
        when(interviewStepService.update(eq(1L), any(InterviewStepDto.class))).thenReturn(step);
        when(interviewStepService.updateStatus(1L, StepStatus.VALIDATED)).thenReturn(step);

        assertThat(interviewStepController.updateInterviewStep(1L, step)).isEqualTo(step);
        assertThat(interviewStepController.updateInterviewStepStatus(1L, StepStatus.VALIDATED)).isEqualTo(step);
        verify(interviewStepService).updateStatus(1L, StepStatus.VALIDATED);
    }

    @Test
    void deleteInterviewStep_ShouldReturnNoContent() {
        ResponseEntity<Void> response = interviewStepController.deleteInterviewStep(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(accessGuard).requireInterviewStep(1L);
        verify(interviewStepService).delete(1L);
    }
}
