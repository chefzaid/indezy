package dev.swirlit.indezy.model;

import dev.swirlit.indezy.constants.UserPreferenceDefaults;
import dev.swirlit.indezy.model.enums.DigestSection;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
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

    /** Days the freelancer aims to bill in a year, compared on the dashboard with the validated activity reports. */
    @Column(name = "billed_days_target")
    private Integer billedDaysTarget;

    /** The longest acceptable drive to a mission, one way at rush hour, in minutes. */
    @Column(name = "max_drive_minutes")
    private Integer maxDriveMinutes;

    /** The longest acceptable public transport trip to a mission, one way at rush hour, in minutes. */
    @Column(name = "max_transit_minutes")
    private Integer maxTransitMinutes;

    /** How much each part of the offers' match score counts: 0 not at all, 1 as usual (null), 2 double; skills 1 or 2. */
    /** The web push events the account turned off, comma-separated ({@code PushEvent} names); null or empty for none. */
    @Column(name = "push_events_off", length = 100)
    private String pushEventsOff;

    @Column(name = "match_weight_skills")
    private Integer matchWeightSkills;

    @Column(name = "match_weight_rate")
    private Integer matchWeightRate;

    @Column(name = "match_weight_place")
    private Integer matchWeightPlace;

    @Column(name = "match_weight_start")
    private Integer matchWeightStart;

    /** What a kilometre driven to a mission costs, in euros: the effective daily rate takes it off. */
    @Column(name = "km_rate", precision = 4, scale = 2)
    private BigDecimal kmRate;

    /** The monthly public transport pass (a Navigo, say), in euros: the effective daily rate takes it off. */
    @Column(name = "transit_pass_monthly", precision = 6, scale = 2)
    private BigDecimal transitPassMonthly;

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

    /** The day the browsers were last told of the follow-ups due; null before the first time. */
    @Column(name = "follow_ups_pushed_on")
    private LocalDate followUpsPushedOn;

    /** How many new offers the saved searches had when the browsers were last told; null before the first time. */
    @Column(name = "offers_pushed_total")
    private Long offersPushedTotal;

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

    /**
     * Whether the account lets its won missions' daily rates count in the network's rate benchmark, anonymized and only
     * in figures five freelancers or more back (docs/adr/0014-aggregated-market-data.md); off until turned on.
     */
    @Column(name = "share_rates")
    private Boolean shareRates;

    public Boolean getShareRates() {
        return Boolean.TRUE.equals(shareRates);
    }

    /**
     * Whether the account's records of the ESNs it dealt with count in the ESN scorecards across members, anonymized and
     * only in figures five freelancers or more back (docs/adr/0016-esn-scorecards-across-members.md); on until turned off.
     */
    @Column(name = "share_esn_records")
    private Boolean shareEsnRecords;

    public Boolean getShareEsnRecords() {
        return !Boolean.FALSE.equals(shareEsnRecords);
    }

    /**
     * Whether Indezy triages the new offers that fit overnight, from the account's AI allowance (the daily autopilot
     * run, docs/features.md#daily-autopilot); off until turned on.
     */
    @Column(name = "autopilot")
    private Boolean autopilot;

    public Boolean getAutopilot() {
        return Boolean.TRUE.equals(autopilot);
    }

    /** The fiscal horsepower of the car driven to missions (3 for 3 CV and less, 7 for 7 CV and more); null when not given. */
    @Column(name = "vehicle_horsepower")
    private Integer vehicleHorsepower;

    /** Whether that car is electric (the kilometre scale gives 20 % more). */
    @Column(name = "vehicle_electric")
    private Boolean vehicleElectric;

    /**
     * Whether the account's email was confirmed: false for an account registered with a password while verification is
     * required, until its link is followed; null for the others (signed in through SSO, or created before), read as yes.
     */
    @Column(name = "email_verified")
    private Boolean emailVerified;

    /** The SHA-256, in hex, of the email confirmation link's secret; null once confirmed. */
    @Column(name = "email_verification_hash", length = 64, unique = true)
    private String emailVerificationHash;

    /** The SHA-256, in hex, of the password reset link's secret, and until when it opens; null when none was asked. */
    @Column(name = "password_reset_hash", length = 64, unique = true)
    private String passwordResetHash;

    @Column(name = "password_reset_expires_at")
    private java.time.Instant passwordResetExpiresAt;

    /** When the last confirmation or reset email went, so asking again and again sends one every two minutes at most. */
    @Column(name = "account_email_sent_at")
    private java.time.Instant accountEmailSentAt;

    /** The member's own Anthropic key, encrypted (AiKeyCipher); null to use Indezy's within its limits. */
    @Column(name = "ai_api_key", length = 600)
    private String aiApiKey;

    /** The model the member's own key uses; null for Indezy's choice. */
    @Column(name = "ai_model", length = 40)
    private String aiModel;

    /** The models the member chose for some tasks with their own key, as {@code TASK=model} pairs joined by commas. */
    @Column(name = "ai_task_models", length = 400)
    private String aiTaskModels;

    /** Years without exchange after which a contact is anonymized every night; null or 0 for never. */
    @Column(name = "contact_retention_years")
    private Integer contactRetentionYears;

    /**
     * Settings an account gets when it is created: the welcome steps, the nightly archiving and the contacts'
     * retention on. Accounts created before keep their own choices.
     */
    public void applyNewAccountDefaults() {
        onboardingPending = true;
        autoArchive = true;
        contactRetentionYears = UserPreferenceDefaults.NEW_ACCOUNT_CONTACT_RETENTION_YEARS;
    }

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

    /** Whether messages unread after an hour are emailed to the member; a null column reads as on. */
    @Column(name = "message_emails")
    private Boolean messageEmails = true;

    /** The unread messages email's one-click unsubscribe token, set when the first one is sent. */
    @Column(name = "message_email_token", unique = true, length = 64)
    private String messageEmailToken;

    /** When the latest unread messages email was sent, so at most one goes every three hours. */
    @Column(name = "message_email_sent_at")
    private Instant messageEmailSentAt;

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
