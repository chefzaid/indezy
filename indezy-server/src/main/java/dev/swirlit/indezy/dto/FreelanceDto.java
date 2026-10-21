package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.EmploymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;

// Spring MVC logs bodies through toString at DEBUG: the id alone, never the person (see docs/security.md).
@Data
@ToString(onlyExplicitlyIncluded = true)
public class FreelanceDto {
    @ToString.Include
    private Long id;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    private String phone;
    private LocalDate birthDate;
    private String address;
    private String city;

    @NotNull(message = "Employment status is required")
    private EmploymentStatus status;

    private Integer noticePeriodInDays;
    private LocalDate availabilityDate;
    private Double reversionRate;
    private Double incomeTaxRate;
    private String cvFilePath;

    // Computed fields
    private String fullName;
    private Integer totalProjects;
    private Double averageDailyRate;
}
