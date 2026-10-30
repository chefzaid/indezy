package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.util.WholeNumberDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Spring MVC logs bodies through toString at DEBUG: the id alone, never the person (see docs/security.md).
@Data
@ToString(onlyExplicitlyIncluded = true)
public class UserDto {
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
    private String avatar;
    private String bio;
    private String company;
    private String position;
    private String website;
    private String linkedin;
    private String github;
    @Size(max = 50)
    private List<@NotBlank @Size(max = 60) String> skills;
    /** A level and years of experience for some of the skills, keyed by skill. */
    private Map<String, @Valid SkillDetailDto> skillDetails;
    private List<String> languages;

    @Min(1)
    @JsonDeserialize(using = WholeNumberDeserializer.class)
    private Integer targetDailyRate;

    /** When the workspace is next available (its freelance record's), which offers' start dates are matched against. */
    private LocalDate availabilityDate;

    // Computed fields
    private String fullName;
    private UserNotificationSettingsDto notifications;
    private UserPreferencesDto preferences;
}
