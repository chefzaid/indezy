package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;

// Spring MVC logs bodies through toString at DEBUG: the id alone, never the person (see docs/security.md).
@Data
@ToString(onlyExplicitlyIncluded = true)
public class ContactDto {
    @ToString.Include
    private Long id;

    @NotBlank(message = "First name is required")
    private String firstName;

    private String lastName;

    @Email(message = "Email should be valid")
    private String email;

    private String phone;
    private String notes;

    // Related entities
    private Long clientId;
    private String clientName;
    private Long freelanceId;
    /** Whom the contact reports to, in the same workspace; null for nobody. */
    private Long managerId;
    private String managerName;

    // Computed fields
    private String fullName;
    /** The day of the latest recorded exchange; read-only. */
    private LocalDate lastContactedOn;
}
