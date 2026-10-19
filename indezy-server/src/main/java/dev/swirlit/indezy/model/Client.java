package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ActivityReportLayout;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
@Getter
@Setter
public class Client extends BaseEntity {

    @NotBlank
    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "address")
    private String address;

    @NotBlank
    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "domain")
    private String domain;

    @NotNull
    @Column(name = "is_final", nullable = false)
    private Boolean isFinal;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Quality rating from 1 (poor) to 5 (excellent); null when not yet rated. */
    @Column(name = "rating")
    private Integer rating;

    /** Flags clients to avoid (payment delays, ghosting, bad process). */
    @Column(name = "is_blacklisted", nullable = false, columnDefinition = "boolean default false")
    private Boolean isBlacklisted = false;

    /** Explanation shown when the client is blacklisted. */
    @Column(name = "blacklist_reason", columnDefinition = "TEXT")
    private String blacklistReason;

    /*
     * Template of the activity report PDFs of missions through this client (the ESN when the mission has one,
     * otherwise the final client). Null fields use the defaults: standard title, daily table, location column, French.
     */
    @Column(name = "activity_report_title", length = 120)
    private String activityReportTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_report_layout")
    private ActivityReportLayout activityReportLayout;

    @Column(name = "activity_report_shows_location")
    private Boolean activityReportShowsLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_report_language")
    private DocumentLanguage activityReportLanguage;

    /** Free text printed at the bottom of the PDF, such as where to send the signed report. */
    @Column(name = "activity_report_footer", length = 500)
    private String activityReportFooter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Project> projects = new ArrayList<>();

    @OneToMany(mappedBy = "middleman", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Project> middlemanProjects = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Contact> contacts = new ArrayList<>();

    /** Coerce null to false so the non-nullable column is always populated. */
    public void setIsBlacklisted(final Boolean isBlacklisted) {
        this.isBlacklisted = isBlacklisted != null && isBlacklisted;
    }

    // Custom getters and setters for collections to prevent EI_EXPOSE_REP
    public List<Project> getProjects() {
        return projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public void setProjects(final List<Project> projects) {
        this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public List<Project> getMiddlemanProjects() {
        return middlemanProjects != null ? new ArrayList<>(middlemanProjects) : new ArrayList<>();
    }

    public void setMiddlemanProjects(final List<Project> middlemanProjects) {
        this.middlemanProjects = middlemanProjects != null ? new ArrayList<>(middlemanProjects) : new ArrayList<>();
    }

    public List<Contact> getContacts() {
        return contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
    }

    public void setContacts(final List<Contact> contacts) {
        this.contacts = contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
    }
}
