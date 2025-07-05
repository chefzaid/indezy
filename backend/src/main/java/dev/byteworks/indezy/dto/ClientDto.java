package dev.byteworks.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClientDto {
    private Long id;

    @NotBlank(message = "Company name is required")
    private String companyName;

    private String address;

    @NotBlank(message = "City is required")
    private String city;

    private String domain;

    @NotNull(message = "Final client status is required")
    private Boolean isFinal;

    private String notes;

    // Related entities
    @NotNull(message = "Freelance ID is required")
    private Long freelanceId;

    // Computed fields
    private Integer totalProjects;
    private Integer totalContacts;
    private Double averageProjectRating;
    private Double averageDailyRate;

    // Related collections (for detailed views)
    private java.util.List<dev.byteworks.indezy.dto.ProjectDto> projects;
    private java.util.List<dev.byteworks.indezy.dto.ContactDto> contacts;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
