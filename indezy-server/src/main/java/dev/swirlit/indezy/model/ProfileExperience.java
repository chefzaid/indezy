package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A position the freelancer held before or outside Indezy (see docs/features.md#experience-and-education): printed in
 * the dossier de compétences after the won missions, and on the public profile when chosen.
 */
@Entity
@Table(name = "profile_experiences")
@Getter
@Setter
public class ProfileExperience extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    /** The client or employer. */
    @Column(name = "employer", nullable = false, length = 200)
    private String employer;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    /** Null while it runs. */
    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "stack", length = 500)
    private String stack;

    @Column(name = "shown_publicly", nullable = false)
    private boolean shownPublicly;
}
