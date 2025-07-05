package dev.byteworks.indezy.dto;

import dev.byteworks.indezy.model.enums.WorkMode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ProjectDtoTest {

    private Validator validator;
    private ProjectDto projectDto;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        projectDto = new ProjectDto();
        projectDto.setRole("Full Stack Developer");
        projectDto.setDescription("Test project description");
        projectDto.setTechStack("Java, Spring Boot, Angular");
        projectDto.setDailyRate(600);
        projectDto.setWorkMode(WorkMode.HYBRID);
        projectDto.setRemoteDaysPerMonth(15);
        projectDto.setOnsiteDaysPerMonth(5);
        projectDto.setStartDate(LocalDate.of(2024, 1, 15));
        projectDto.setDurationInMonths(6);
        projectDto.setDaysPerYear(220);
        projectDto.setPersonalRating(4);
        projectDto.setFreelanceId(1L);
        projectDto.setClientId(1L);
    }

    @Test
    void validProjectDto_ShouldPassValidation() {
        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(projectDto);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void projectDto_WithBlankRole_ShouldFailValidation() {
        // Given
        projectDto.setRole("");

        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(projectDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<ProjectDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("role");
        assertThat(violation.getMessage()).isEqualTo("Role is required");
    }

    @Test
    void projectDto_WithNullRole_ShouldFailValidation() {
        // Given
        projectDto.setRole(null);

        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(projectDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<ProjectDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("role");
        assertThat(violation.getMessage()).isEqualTo("Role is required");
    }

    @Test
    void projectDto_WithNullDailyRate_ShouldFailValidation() {
        // Given
        projectDto.setDailyRate(null);

        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(projectDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<ProjectDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("dailyRate");
        assertThat(violation.getMessage()).isEqualTo("Daily rate is required");
    }

    @Test
    void projectDto_WithPersonalRatingBelowMin_ShouldFailValidation() {
        // Given
        projectDto.setPersonalRating(0);

        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(projectDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<ProjectDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("personalRating");
        assertThat(violation.getMessage()).isEqualTo("Personal rating must be between 1 and 5");
    }

    @Test
    void projectDto_WithPersonalRatingAboveMax_ShouldFailValidation() {
        // Given
        projectDto.setPersonalRating(6);

        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(projectDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<ProjectDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("personalRating");
        assertThat(violation.getMessage()).isEqualTo("Personal rating must be between 1 and 5");
    }

    @Test
    void projectDto_WithValidPersonalRating_ShouldPassValidation() {
        // Given
        projectDto.setPersonalRating(1);

        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(projectDto);

        // Then
        assertThat(violations).isEmpty();

        // Test upper bound
        projectDto.setPersonalRating(5);
        violations = validator.validate(projectDto);
        assertThat(violations).isEmpty();
    }

    @Test
    void projectDto_WithMultipleValidationErrors_ShouldReturnAllViolations() {
        // Given
        projectDto.setRole("");
        projectDto.setDailyRate(null);
        projectDto.setPersonalRating(10);

        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(projectDto);

        // Then
        assertThat(violations).hasSize(3);
        
        Set<String> violatedProperties = violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
        
        assertThat(violatedProperties).containsExactlyInAnyOrder("role", "dailyRate", "personalRating");
    }

    @Test
    void projectDto_WithOptionalFields_ShouldPassValidation() {
        // Given - Set only required fields
        ProjectDto minimalDto = new ProjectDto();
        minimalDto.setRole("Developer");
        minimalDto.setDailyRate(500);

        // When
        Set<ConstraintViolation<ProjectDto>> violations = validator.validate(minimalDto);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void projectDto_GettersAndSetters_ShouldWorkCorrectly() {
        // Given
        Long id = 1L;
        String role = "Full Stack Developer";
        String description = "Test description";
        String techStack = "Java, Spring";
        Integer dailyRate = 600;
        WorkMode workMode = WorkMode.REMOTE;
        Integer remoteDays = 20;
        Integer onsiteDays = 0;
        String advantages = "Great team";
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        Integer duration = 6;
        Integer renewal = 3;
        Integer daysPerYear = 220;
        String link = "https://example.com";
        Integer rating = 4;
        String notes = "Good project";
        Long freelanceId = 1L;
        Long clientId = 2L;
        String clientName = "Test Client";
        Long middlemanId = 3L;
        String middlemanName = "Test Middleman";
        Long sourceId = 4L;
        String sourceName = "Test Source";
        Integer totalRevenue = 79200;

        // When
        projectDto.setId(id);
        projectDto.setRole(role);
        projectDto.setDescription(description);
        projectDto.setTechStack(techStack);
        projectDto.setDailyRate(dailyRate);
        projectDto.setWorkMode(workMode);
        projectDto.setRemoteDaysPerMonth(remoteDays);
        projectDto.setOnsiteDaysPerMonth(onsiteDays);
        projectDto.setAdvantages(advantages);
        projectDto.setStartDate(startDate);
        projectDto.setDurationInMonths(duration);
        projectDto.setOrderRenewalInMonths(renewal);
        projectDto.setDaysPerYear(daysPerYear);
        projectDto.setLink(link);
        projectDto.setPersonalRating(rating);
        projectDto.setNotes(notes);
        projectDto.setFreelanceId(freelanceId);
        projectDto.setClientId(clientId);
        projectDto.setClientName(clientName);
        projectDto.setMiddlemanId(middlemanId);
        projectDto.setMiddlemanName(middlemanName);
        projectDto.setSourceId(sourceId);
        projectDto.setSourceName(sourceName);
        projectDto.setTotalRevenue(totalRevenue);

        // Then
        assertThat(projectDto.getId()).isEqualTo(id);
        assertThat(projectDto.getRole()).isEqualTo(role);
        assertThat(projectDto.getDescription()).isEqualTo(description);
        assertThat(projectDto.getTechStack()).isEqualTo(techStack);
        assertThat(projectDto.getDailyRate()).isEqualTo(dailyRate);
        assertThat(projectDto.getWorkMode()).isEqualTo(workMode);
        assertThat(projectDto.getRemoteDaysPerMonth()).isEqualTo(remoteDays);
        assertThat(projectDto.getOnsiteDaysPerMonth()).isEqualTo(onsiteDays);
        assertThat(projectDto.getAdvantages()).isEqualTo(advantages);
        assertThat(projectDto.getStartDate()).isEqualTo(startDate);
        assertThat(projectDto.getDurationInMonths()).isEqualTo(duration);
        assertThat(projectDto.getOrderRenewalInMonths()).isEqualTo(renewal);
        assertThat(projectDto.getDaysPerYear()).isEqualTo(daysPerYear);
        assertThat(projectDto.getLink()).isEqualTo(link);
        assertThat(projectDto.getPersonalRating()).isEqualTo(rating);
        assertThat(projectDto.getNotes()).isEqualTo(notes);
        assertThat(projectDto.getFreelanceId()).isEqualTo(freelanceId);
        assertThat(projectDto.getClientId()).isEqualTo(clientId);
        assertThat(projectDto.getClientName()).isEqualTo(clientName);
        assertThat(projectDto.getMiddlemanId()).isEqualTo(middlemanId);
        assertThat(projectDto.getMiddlemanName()).isEqualTo(middlemanName);
        assertThat(projectDto.getSourceId()).isEqualTo(sourceId);
        assertThat(projectDto.getSourceName()).isEqualTo(sourceName);
        assertThat(projectDto.getTotalRevenue()).isEqualTo(totalRevenue);
    }

    @Test
    void projectDto_EqualsAndHashCode_ShouldWorkCorrectly() {
        // Given
        ProjectDto dto1 = new ProjectDto();
        dto1.setId(1L);
        dto1.setRole("Developer");
        dto1.setDailyRate(500);

        ProjectDto dto2 = new ProjectDto();
        dto2.setId(1L);
        dto2.setRole("Developer");
        dto2.setDailyRate(500);

        ProjectDto dto3 = new ProjectDto();
        dto3.setId(2L);
        dto3.setRole("Developer");
        dto3.setDailyRate(500);

        // Then
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        assertThat(dto1).isNotEqualTo(dto3);
        assertThat(dto1.hashCode()).isNotEqualTo(dto3.hashCode());
    }

    @Test
    void projectDto_ToString_ShouldContainAllFields() {
        // When
        String toString = projectDto.toString();

        // Then
        assertThat(toString).contains("role=Full Stack Developer");
        assertThat(toString).contains("dailyRate=600");
        assertThat(toString).contains("workMode=HYBRID");
        assertThat(toString).contains("personalRating=4");
    }
}
