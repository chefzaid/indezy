package dev.byteworks.indezy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class UserDto {
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
    private List<String> skills;
    private List<String> languages;
    private String timezone;
    private String currency;

    // Computed fields
    private String fullName;
    private UserNotificationSettingsDto notifications;
    private UserPreferencesDto preferences;
}
