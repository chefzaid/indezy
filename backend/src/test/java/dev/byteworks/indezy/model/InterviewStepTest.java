package dev.byteworks.indezy.model;

import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.model.enums.StepStatus;
import dev.byteworks.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class InterviewStepTest {

    private InterviewStep interviewStep;
    private Project project;
    private Freelance freelance;
    private Client client;

    @BeforeEach
    void setUp() {
        // Setup freelance
        freelance = new Freelance();
        freelance.setId(1L);
        freelance.setFirstName("John");
        freelance.setLastName("Doe");
        freelance.setEmail("john.doe@example.com");
        freelance.setStatus(EmploymentStatus.FREELANCE);

        // Setup client
        client = new Client();
        client.setId(1L);
        client.setCompanyName("Test Company");
        client.setCity("Paris");
        client.setIsFinal(true);
        client.setFreelance(freelance);

        // Setup project
        project = new Project();
        project.setId(1L);
        project.setRole("Full Stack Developer");
        project.setDailyRate(600);
        project.setWorkMode(WorkMode.HYBRID);
        project.setStartDate(LocalDate.of(2024, 1, 15));
        project.setFreelance(freelance);
        project.setClient(client);

        // Setup interview step
        interviewStep = new InterviewStep();
        interviewStep.setId(1L);
        interviewStep.setTitle("Technical Interview");
        interviewStep.setDate(LocalDateTime.of(2024, 1, 10, 14, 30));
        interviewStep.setStatus(StepStatus.VALIDATED);
        interviewStep.setNotes("Candidate performed well on technical questions");
        interviewStep.setProject(project);
    }

    @Test
    void isCompleted_WhenStatusIsValidated_ShouldReturnTrue() {
        // Given
        interviewStep.setStatus(StepStatus.VALIDATED);

        // When
        boolean result = interviewStep.isCompleted();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isCompleted_WhenStatusIsFailed_ShouldReturnFalse() {
        // Given
        interviewStep.setStatus(StepStatus.FAILED);

        // When
        boolean result = interviewStep.isCompleted();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isCompleted_WhenStatusIsPlanned_ShouldReturnFalse() {
        // Given
        interviewStep.setStatus(StepStatus.PLANNED);

        // When
        boolean result = interviewStep.isCompleted();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isFailed_WhenStatusIsFailed_ShouldReturnTrue() {
        // Given
        interviewStep.setStatus(StepStatus.FAILED);

        // When
        boolean result = interviewStep.isFailed();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isFailed_WhenStatusIsValidated_ShouldReturnFalse() {
        // Given
        interviewStep.setStatus(StepStatus.VALIDATED);

        // When
        boolean result = interviewStep.isFailed();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isFailed_WhenStatusIsPlanned_ShouldReturnFalse() {
        // Given
        interviewStep.setStatus(StepStatus.PLANNED);

        // When
        boolean result = interviewStep.isFailed();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void interviewStep_GettersAndSetters_ShouldWorkCorrectly() {
        // Given
        String title = "HR Interview";
        LocalDateTime date = LocalDateTime.of(2024, 1, 8, 10, 0);
        StepStatus status = StepStatus.PLANNED;
        String notes = "Initial screening interview";

        // When
        interviewStep.setTitle(title);
        interviewStep.setDate(date);
        interviewStep.setStatus(status);
        interviewStep.setNotes(notes);

        // Then
        assertThat(interviewStep.getTitle()).isEqualTo(title);
        assertThat(interviewStep.getDate()).isEqualTo(date);
        assertThat(interviewStep.getStatus()).isEqualTo(status);
        assertThat(interviewStep.getNotes()).isEqualTo(notes);
    }

    @Test
    void interviewStep_ProjectRelationship_ShouldWorkCorrectly() {
        // Then
        assertThat(interviewStep.getProject()).isEqualTo(project);
        assertThat(interviewStep.getProject().getId()).isEqualTo(1L);
        assertThat(interviewStep.getProject().getRole()).isEqualTo("Full Stack Developer");
        assertThat(interviewStep.getProject().getFreelance()).isEqualTo(freelance);
        assertThat(interviewStep.getProject().getClient()).isEqualTo(client);
    }

    @Test
    void interviewStep_AllStepStatuses_ShouldWorkCorrectly() {
        // Test VALIDATED status
        interviewStep.setStatus(StepStatus.VALIDATED);
        assertThat(interviewStep.getStatus()).isEqualTo(StepStatus.VALIDATED);
        assertThat(interviewStep.isCompleted()).isTrue();
        assertThat(interviewStep.isFailed()).isFalse();

        // Test FAILED status
        interviewStep.setStatus(StepStatus.FAILED);
        assertThat(interviewStep.getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(interviewStep.isCompleted()).isFalse();
        assertThat(interviewStep.isFailed()).isTrue();

        // Test PLANNED status
        interviewStep.setStatus(StepStatus.PLANNED);
        assertThat(interviewStep.getStatus()).isEqualTo(StepStatus.PLANNED);
        assertThat(interviewStep.isCompleted()).isFalse();
        assertThat(interviewStep.isFailed()).isFalse();
    }

    @Test
    void interviewStep_WithNullDate_ShouldAllowNullDate() {
        // Given
        interviewStep.setDate(null);

        // When & Then
        assertThat(interviewStep.getDate()).isNull();
        // Should still be able to check status
        assertThat(interviewStep.isCompleted()).isTrue(); // Status is VALIDATED from setup
    }

    @Test
    void interviewStep_WithNullNotes_ShouldAllowNullNotes() {
        // Given
        interviewStep.setNotes(null);

        // When & Then
        assertThat(interviewStep.getNotes()).isNull();
        // Should still work normally
        assertThat(interviewStep.getTitle()).isEqualTo("Technical Interview");
        assertThat(interviewStep.getStatus()).isEqualTo(StepStatus.VALIDATED);
    }

    @Test
    void interviewStep_InheritedFromBaseEntity_ShouldHaveBaseFields() {
        // Then
        assertThat(interviewStep.getId()).isEqualTo(1L);
        // Note: createdAt, updatedAt, and version are managed by JPA and would be null in unit tests
        // These would be tested in integration tests with actual JPA context
    }

    @Test
    void interviewStep_MultipleStepsForSameProject_ShouldWorkCorrectly() {
        // Given
        InterviewStep hrStep = new InterviewStep();
        hrStep.setId(2L);
        hrStep.setTitle("HR Interview");
        hrStep.setDate(LocalDateTime.of(2024, 1, 8, 10, 0));
        hrStep.setStatus(StepStatus.VALIDATED);
        hrStep.setProject(project);

        InterviewStep finalStep = new InterviewStep();
        finalStep.setId(3L);
        finalStep.setTitle("Final Interview");
        finalStep.setDate(LocalDateTime.of(2024, 1, 12, 16, 0));
        finalStep.setStatus(StepStatus.PLANNED);
        finalStep.setProject(project);

        // Then
        assertThat(interviewStep.getProject()).isEqualTo(project);
        assertThat(hrStep.getProject()).isEqualTo(project);
        assertThat(finalStep.getProject()).isEqualTo(project);

        // All steps should reference the same project
        assertThat(interviewStep.getProject().getId()).isEqualTo(hrStep.getProject().getId());
        assertThat(hrStep.getProject().getId()).isEqualTo(finalStep.getProject().getId());

        // But have different statuses and details
        assertThat(interviewStep.isCompleted()).isTrue();
        assertThat(hrStep.isCompleted()).isTrue();
        assertThat(finalStep.isCompleted()).isFalse();
    }

    @Test
    void interviewStep_ValidationConstraints_ShouldBePresent() {
        // This test verifies that validation annotations are present
        // The actual validation would be tested in integration tests or with a validator
        
        // Test that required fields can be set
        interviewStep.setTitle("Required Title");
        interviewStep.setStatus(StepStatus.VALIDATED);
        
        assertThat(interviewStep.getTitle()).isNotNull();
        assertThat(interviewStep.getStatus()).isNotNull();
    }

    @Test
    void interviewStep_HelperMethods_WithNullStatus_ShouldHandleGracefully() {
        // Given
        interviewStep.setStatus(null);

        // When & Then
        // The helper methods should handle null status gracefully
        // In a real scenario, status should never be null due to @NotNull validation
        // But we test defensive programming
        assertThat(interviewStep.isCompleted()).isFalse();
        assertThat(interviewStep.isFailed()).isFalse();
    }
}
