package dev.byteworks.indezy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContactDto {
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

    // Computed fields
    private String fullName;
}
