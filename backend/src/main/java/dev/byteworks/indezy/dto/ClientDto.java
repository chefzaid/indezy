package dev.byteworks.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private List<ProjectDto> projects;
    private List<ContactDto> contacts;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Custom getters and setters for collections to prevent EI_EXPOSE_REP
    public List<ProjectDto> getProjects() {
        return projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public void setProjects(final List<ProjectDto> projects) {
        this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public List<ContactDto> getContacts() {
        return contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
    }

    public void setContacts(final List<ContactDto> contacts) {
        this.contacts = contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
    }
}
