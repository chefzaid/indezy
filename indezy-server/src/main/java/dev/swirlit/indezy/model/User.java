package dev.swirlit.indezy.model;

import dev.swirlit.indezy.constants.UserPreferenceDefaults;
import dev.swirlit.indezy.model.enums.DigestSection;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    public static final int PUBLIC_PROFILE_SLUG_MAX = 40;

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

    /** Uploaded avatar stored as a small data URL, so it survives read-only and ephemeral pod filesystems. */
    @Column(name = "avatar_image", columnDefinition = "TEXT")
    private String avatarImage;

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

    /** A level and years of experience for some of the skills, keyed by the skill as {@code skills} writes it. */
    @ElementCollection
    @CollectionTable(name = "user_skill_details", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "skill", length = 60)
    private Map<String, SkillDetail> skillDetails = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "user_languages", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "language")
    private List<String> languages = new ArrayList<>();

    @Column(name = "target_daily_rate")
    private Integer targetDailyRate;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "last_password_change")
    private LocalDateTime lastPasswordChange;

    /** Set when the account is soft-deleted; such accounts can no longer log in. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // User Preferences
    @Column(name = "theme")
    private String theme = "light";

    @Column(name = "language_preference")
    private String languagePreference = "en";

    @Column(name = "inactivity_days")
    private Integer inactivityDays = UserPreferenceDefaults.INACTIVITY_DAYS;

    /** Existing accounts can have a null column after Hibernate adds this preference. */
    public Integer getInactivityDays() {
        return inactivityDays != null ? inactivityDays : UserPreferenceDefaults.INACTIVITY_DAYS;
    }

    @Column(name = "follow_up_days")
    private Integer followUpDays = UserPreferenceDefaults.FOLLOW_UP_DAYS;

    /** Existing accounts can have a null column after Hibernate adds this preference. */
    public Integer getFollowUpDays() {
        return followUpDays != null ? followUpDays : UserPreferenceDefaults.FOLLOW_UP_DAYS;
    }

    /** When an administrator disabled the account: it can no longer sign in, and its tokens stop working. */
    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    /** Whether the account may use the app: neither deleted nor disabled. */
    public boolean isActive() {
        return deletedAt == null && disabledAt == null;
    }

    /** Address name of the public profile, at /p/{slug}: lower-case letters, digits and dashes, one account each. */
    @Column(name = "public_profile_slug", length = PUBLIC_PROFILE_SLUG_MAX, unique = true)
    private String publicProfileSlug;

    /** Whether anyone with the link sees the public profile; off until its owner turns it on. */
    @Column(name = "public_profile_enabled")
    private Boolean publicProfileEnabled = false;

    public Boolean getPublicProfileEnabled() {
        return Boolean.TRUE.equals(publicProfileEnabled);
    }

    /** Set on accounts created since the welcome steps exist, until their owner finishes or skips them. */
    @Column(name = "onboarding_pending")
    private Boolean onboardingPending = false;

    /** Accounts older than the welcome steps have a null column once Hibernate adds it: nothing pending. */
    public Boolean getOnboardingPending() {
        return Boolean.TRUE.equals(onboardingPending);
    }

    /** Archive the workspace's opportunities left idle for the inactivity period, every night. */
    @Column(name = "auto_archive")
    private Boolean autoArchive = false;

    /** Existing accounts have a null column once Hibernate adds this preference: off. */
    public Boolean getAutoArchive() {
        return Boolean.TRUE.equals(autoArchive);
    }

    /** Years without exchange after which a contact is anonymized every night; null or 0 for never. */
    @Column(name = "contact_retention_years")
    private Integer contactRetentionYears;

    // Notification Settings
    @Column(name = "weekly_reports")
    private Boolean weeklyReports = false;

    /** The weekly digest's one-click unsubscribe token, set when it is first turned on. */
    @Column(name = "weekly_digest_token", unique = true, length = 64)
    private String weeklyDigestToken;

    /** The Monday of the last week whose digest was sent. */
    @Column(name = "weekly_digest_sent_on")
    private LocalDate weeklyDigestSentOn;

    /** The weekly digest's sections the account left out, comma-separated ({@link DigestSection}); null for none. */
    @Column(name = "weekly_digest_skipped", length = 200)
    private String weeklyDigestSkipped;

    /** Whether a call booked through the booking link is emailed to the account; a null column reads as on. */
    @Column(name = "booking_emails")
    private Boolean bookingEmails = true;

    /** Whether the menu counts the new offers of the saved searches (in-app); a null column reads as on. */
    @Column(name = "new_offers_badge")
    private Boolean newOffersBadge = true;

    /** The dashboard's blocks as arranged (see {@code DashboardLayout}); null until the account arranges them. */
    @Column(name = "dashboard_layout", length = 200)
    private String dashboardLayout;

    // Security Settings
    @Column(name = "two_factor_enabled")
    private Boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    /** The weekly digest's sections the account keeps: all but those it left out, so a new section is in at first. */
    public Set<DigestSection> getWeeklyDigestSections() {
        Set<DigestSection> sections = EnumSet.allOf(DigestSection.class);
        if (weeklyDigestSkipped != null && !weeklyDigestSkipped.isBlank()) {
            for (String name : weeklyDigestSkipped.split(",")) {
                Arrays.stream(DigestSection.values()).filter(section -> section.name().equals(name)).findFirst().ifPresent(sections::remove);
            }
        }
        return sections;
    }

    public void setWeeklyDigestSections(Set<DigestSection> sections) {
        weeklyDigestSkipped = EnumSet.complementOf(sections.isEmpty() ? EnumSet.noneOf(DigestSection.class) : EnumSet.copyOf(sections))
            .stream().map(Enum::name).collect(Collectors.joining(","));
    }

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

    public Map<String, SkillDetail> getSkillDetails() {
        return skillDetails != null ? new HashMap<>(skillDetails) : new HashMap<>();
    }

    /** Keeps the details that say something about one of the profile's skills: set the skills first. */
    public void setSkillDetails(Map<String, SkillDetail> details) {
        Map<String, SkillDetail> kept = new HashMap<>();
        if (details != null) {
            details.forEach((skill, detail) -> {
                if (detail != null && !detail.isEmpty() && skills.contains(skill)) {
                    kept.put(skill, detail);
                }
            });
        }
        this.skillDetails = kept;
    }

    public List<String> getLanguages() {
        return languages != null ? new ArrayList<>(languages) : new ArrayList<>();
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages != null ? new ArrayList<>(languages) : new ArrayList<>();
    }
}
