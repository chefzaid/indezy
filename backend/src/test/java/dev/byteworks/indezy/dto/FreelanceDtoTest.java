package dev.byteworks.indezy.dto;

import dev.byteworks.indezy.model.enums.EmploymentStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FreelanceDtoTest {

    private Validator validator;
    private FreelanceDto freelanceDto;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        freelanceDto = new FreelanceDto();
        freelanceDto.setFirstName("John");
        freelanceDto.setLastName("Doe");
        freelanceDto.setEmail("john.doe@example.com");
        freelanceDto.setPhone("+33 6 12 34 56 78");
        freelanceDto.setBirthDate(LocalDate.of(1990, 5, 15));
        freelanceDto.setAddress("123 Rue de la Paix");
        freelanceDto.setCity("Paris");
        freelanceDto.setStatus(EmploymentStatus.FREELANCE);
        freelanceDto.setNoticePeriodInDays(30);
        freelanceDto.setAvailabilityDate(LocalDate.now().plusDays(30));
        freelanceDto.setReversionRate(0.15);
    }

    @Test
    void validFreelanceDto_ShouldPassValidation() {
        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void freelanceDto_WithBlankFirstName_ShouldFailValidation() {
        // Given
        freelanceDto.setFirstName("");

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<FreelanceDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("firstName");
        assertThat(violation.getMessage()).isEqualTo("First name is required");
    }

    @Test
    void freelanceDto_WithNullFirstName_ShouldFailValidation() {
        // Given
        freelanceDto.setFirstName(null);

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<FreelanceDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("firstName");
        assertThat(violation.getMessage()).isEqualTo("First name is required");
    }

    @Test
    void freelanceDto_WithBlankLastName_ShouldFailValidation() {
        // Given
        freelanceDto.setLastName("");

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<FreelanceDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("lastName");
        assertThat(violation.getMessage()).isEqualTo("Last name is required");
    }

    @Test
    void freelanceDto_WithNullLastName_ShouldFailValidation() {
        // Given
        freelanceDto.setLastName(null);

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<FreelanceDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("lastName");
        assertThat(violation.getMessage()).isEqualTo("Last name is required");
    }

    @Test
    void freelanceDto_WithBlankEmail_ShouldFailValidation() {
        // Given
        freelanceDto.setEmail("");

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<FreelanceDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("email");
        assertThat(violation.getMessage()).isEqualTo("Email is required");
    }

    @Test
    void freelanceDto_WithNullEmail_ShouldFailValidation() {
        // Given
        freelanceDto.setEmail(null);

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<FreelanceDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("email");
        assertThat(violation.getMessage()).isEqualTo("Email is required");
    }

    @Test
    void freelanceDto_WithInvalidEmail_ShouldFailValidation() {
        // Given
        freelanceDto.setEmail("invalid-email");

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<FreelanceDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("email");
        assertThat(violation.getMessage()).isEqualTo("Email should be valid");
    }

    @Test
    void freelanceDto_WithValidEmail_ShouldPassValidation() {
        // Given
        freelanceDto.setEmail("valid@example.com");

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void freelanceDto_WithNullStatus_ShouldFailValidation() {
        // Given
        freelanceDto.setStatus(null);

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<FreelanceDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("status");
        assertThat(violation.getMessage()).isEqualTo("Employment status is required");
    }

    @Test
    void freelanceDto_WithMultipleValidationErrors_ShouldReturnAllViolations() {
        // Given
        freelanceDto.setFirstName("");
        freelanceDto.setLastName(null);
        freelanceDto.setEmail("invalid-email");
        freelanceDto.setStatus(null);

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(freelanceDto);

        // Then
        assertThat(violations).hasSize(4);
        
        Set<String> violatedProperties = violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
        
        assertThat(violatedProperties).containsExactlyInAnyOrder("firstName", "lastName", "email", "status");
    }

    @Test
    void freelanceDto_WithMinimalValidData_ShouldPassValidation() {
        // Given - Set only required fields
        FreelanceDto minimalDto = new FreelanceDto();
        minimalDto.setFirstName("John");
        minimalDto.setLastName("Doe");
        minimalDto.setEmail("john@example.com");
        minimalDto.setStatus(EmploymentStatus.FREELANCE);

        // When
        Set<ConstraintViolation<FreelanceDto>> violations = validator.validate(minimalDto);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void freelanceDto_GettersAndSetters_ShouldWorkCorrectly() {
        // Given
        Long id = 1L;
        String firstName = "John";
        String lastName = "Doe";
        String email = "john.doe@example.com";
        String phone = "+33 6 12 34 56 78";
        LocalDate birthDate = LocalDate.of(1990, 5, 15);
        String address = "123 Rue de la Paix";
        String city = "Paris";
        EmploymentStatus status = EmploymentStatus.FREELANCE;
        Integer noticePeriod = 30;
        LocalDate availabilityDate = LocalDate.now().plusDays(30);
        Double reversionRate = 0.15;
        String cvFilePath = "/path/to/cv.pdf";
        String fullName = "John Doe";
        Integer totalProjects = 5;
        Double averageDailyRate = 575.0;

        // When
        freelanceDto.setId(id);
        freelanceDto.setFirstName(firstName);
        freelanceDto.setLastName(lastName);
        freelanceDto.setEmail(email);
        freelanceDto.setPhone(phone);
        freelanceDto.setBirthDate(birthDate);
        freelanceDto.setAddress(address);
        freelanceDto.setCity(city);
        freelanceDto.setStatus(status);
        freelanceDto.setNoticePeriodInDays(noticePeriod);
        freelanceDto.setAvailabilityDate(availabilityDate);
        freelanceDto.setReversionRate(reversionRate);
        freelanceDto.setCvFilePath(cvFilePath);
        freelanceDto.setFullName(fullName);
        freelanceDto.setTotalProjects(totalProjects);
        freelanceDto.setAverageDailyRate(averageDailyRate);

        // Then
        assertThat(freelanceDto.getId()).isEqualTo(id);
        assertThat(freelanceDto.getFirstName()).isEqualTo(firstName);
        assertThat(freelanceDto.getLastName()).isEqualTo(lastName);
        assertThat(freelanceDto.getEmail()).isEqualTo(email);
        assertThat(freelanceDto.getPhone()).isEqualTo(phone);
        assertThat(freelanceDto.getBirthDate()).isEqualTo(birthDate);
        assertThat(freelanceDto.getAddress()).isEqualTo(address);
        assertThat(freelanceDto.getCity()).isEqualTo(city);
        assertThat(freelanceDto.getStatus()).isEqualTo(status);
        assertThat(freelanceDto.getNoticePeriodInDays()).isEqualTo(noticePeriod);
        assertThat(freelanceDto.getAvailabilityDate()).isEqualTo(availabilityDate);
        assertThat(freelanceDto.getReversionRate()).isEqualTo(reversionRate);
        assertThat(freelanceDto.getCvFilePath()).isEqualTo(cvFilePath);
        assertThat(freelanceDto.getFullName()).isEqualTo(fullName);
        assertThat(freelanceDto.getTotalProjects()).isEqualTo(totalProjects);
        assertThat(freelanceDto.getAverageDailyRate()).isEqualTo(averageDailyRate);
    }

    @Test
    void freelanceDto_EqualsAndHashCode_ShouldWorkCorrectly() {
        // Given
        FreelanceDto dto1 = new FreelanceDto();
        dto1.setId(1L);
        dto1.setFirstName("John");
        dto1.setLastName("Doe");
        dto1.setEmail("john@example.com");
        dto1.setStatus(EmploymentStatus.FREELANCE);

        FreelanceDto dto2 = new FreelanceDto();
        dto2.setId(1L);
        dto2.setFirstName("John");
        dto2.setLastName("Doe");
        dto2.setEmail("john@example.com");
        dto2.setStatus(EmploymentStatus.FREELANCE);

        FreelanceDto dto3 = new FreelanceDto();
        dto3.setId(2L);
        dto3.setFirstName("Jane");
        dto3.setLastName("Smith");
        dto3.setEmail("jane@example.com");
        dto3.setStatus(EmploymentStatus.CDI);

        // Then
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        assertThat(dto1).isNotEqualTo(dto3);
        assertThat(dto1.hashCode()).isNotEqualTo(dto3.hashCode());
    }

    @Test
    void freelanceDto_ToString_ShouldContainAllFields() {
        // When
        String toString = freelanceDto.toString();

        // Then
        assertThat(toString).contains("firstName=John");
        assertThat(toString).contains("lastName=Doe");
        assertThat(toString).contains("email=john.doe@example.com");
        assertThat(toString).contains("status=FREELANCE");
    }
}
