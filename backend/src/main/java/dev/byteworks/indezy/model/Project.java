package dev.byteworks.indezy.model;

import dev.byteworks.indezy.model.enums.WorkMode;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project extends BaseEntity {

    @NotBlank
    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tech_stack")
    private String techStack;

    @NotNull
    @Column(name = "daily_rate", nullable = false)
    private Integer dailyRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode")
    private WorkMode workMode;

    @Column(name = "remote_days_per_month")
    private Integer remoteDaysPerMonth;

    @Column(name = "onsite_days_per_month")
    private Integer onsiteDaysPerMonth;

    @Column(name = "advantages", columnDefinition = "TEXT")
    private String advantages;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "duration_in_months")
    private Integer durationInMonths;

    @Column(name = "order_renewal_in_months")
    private Integer orderRenewalInMonths;

    @Column(name = "days_per_year")
    private Integer daysPerYear;

    @ElementCollection
    @CollectionTable(name = "project_documents", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "document_path")
    private List<String> documents = new ArrayList<>();

    @Column(name = "link")
    private String link;

    @Min(1)
    @Max(5)
    @Column(name = "personal_rating")
    private Integer personalRating;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "middleman_id")
    private Client middleman;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private Source source;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<InterviewStep> steps = new ArrayList<>();

    // Helper methods
    public Integer getTotalRevenue() {
        if (dailyRate != null && daysPerYear != null && durationInMonths != null) {
            return dailyRate * daysPerYear * durationInMonths / 12;
        }
        return null;
    }

    public boolean isRemote() {
        return WorkMode.REMOTE.equals(workMode);
    }

    public boolean isHybrid() {
        return WorkMode.HYBRID.equals(workMode);
    }

    public boolean isOnsite() {
        return WorkMode.ONSITE.equals(workMode);
    }
}
