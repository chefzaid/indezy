package dev.byteworks.indezy.model;

import dev.byteworks.indezy.model.enums.SourceType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sources")
@Getter
@Setter
public class Source extends BaseEntity {

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SourceType type;

    @Column(name = "link")
    private String link;

    @Column(name = "is_listing", nullable = false)
    private Boolean isListing = false;

    @Min(1)
    @Max(5)
    @Column(name = "popularity_rating")
    private Integer popularityRating;

    @Min(1)
    @Max(5)
    @Column(name = "usefulness_rating")
    private Integer usefulnessRating;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Project> projects = new ArrayList<>();

    // Custom getters and setters for collections to prevent EI_EXPOSE_REP
    public List<Project> getProjects() {
        return projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public void setProjects(final List<Project> projects) {
        this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }
}
