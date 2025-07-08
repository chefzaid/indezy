package dev.byteworks.indezy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @NotBlank
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "company")
    private String company;

    @Column(name = "position")
    private String position;

    @Column(name = "website")
    private String website;

    @Column(name = "linkedin")
    private String linkedin;

    @Column(name = "github")
    private String github;

    @ElementCollection
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill")
    private List<String> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_languages", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "language")
    private List<String> languages = new ArrayList<>();

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "currency")
    private String currency;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "last_password_change")
    private LocalDateTime lastPasswordChange;

    // User Preferences
    @Column(name = "theme")
    private String theme = "light";

    @Column(name = "language_preference")
    private String languagePreference = "en";

    @Column(name = "date_format")
    private String dateFormat = "MM/dd/yyyy";

    @Column(name = "time_format")
    private String timeFormat = "12h";

    @Column(name = "default_view")
    private String defaultView = "dashboard";

    @Column(name = "items_per_page")
    private Integer itemsPerPage = 10;

    @Column(name = "auto_save")
    private Boolean autoSave = true;

    // Notification Settings
    @Column(name = "email_notifications")
    private Boolean emailNotifications = true;

    @Column(name = "push_notifications")
    private Boolean pushNotifications = false;

    @Column(name = "project_updates")
    private Boolean projectUpdates = true;

    @Column(name = "client_messages")
    private Boolean clientMessages = true;

    @Column(name = "system_alerts")
    private Boolean systemAlerts = true;

    @Column(name = "weekly_reports")
    private Boolean weeklyReports = false;

    @Column(name = "marketing_emails")
    private Boolean marketingEmails = false;

    // Security Settings
    @Column(name = "two_factor_enabled")
    private Boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserSession> sessions = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserSecurityQuestion> securityQuestions = new HashSet<>();

    // Helper methods
    public String getFullName() {
        return firstName + " " + lastName;
    }

    // Custom getters and setters for collections to prevent EI_EXPOSE_REP
    public List<String> getSkills() {
        return skills != null ? new ArrayList<>(skills) : new ArrayList<>();
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? new ArrayList<>(skills) : new ArrayList<>();
    }

    public List<String> getLanguages() {
        return languages != null ? new ArrayList<>(languages) : new ArrayList<>();
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages != null ? new ArrayList<>(languages) : new ArrayList<>();
    }

    public List<UserSession> getSessions() {
        return sessions != null ? new ArrayList<>(sessions) : new ArrayList<>();
    }

    public void setSessions(List<UserSession> sessions) {
        this.sessions = sessions != null ? new HashSet<>(sessions) : new HashSet<>();
    }

    public List<UserSecurityQuestion> getSecurityQuestions() {
        return securityQuestions != null ? new ArrayList<>(securityQuestions) : new ArrayList<>();
    }

    public void setSecurityQuestions(List<UserSecurityQuestion> securityQuestions) {
        this.securityQuestions = securityQuestions != null ? new HashSet<>(securityQuestions) : new HashSet<>();
    }
}
