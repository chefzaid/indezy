package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A school or degree of the freelancer (see docs/features.md#experience-and-education): printed in the dossier de
 * compétences, and on the public profile when chosen.
 */
@Entity
@Table(name = "profile_educations")
@Getter
@Setter
public class ProfileEducation extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "school", nullable = false, length = 200)
    private String school;

    @Column(name = "degree", length = 200)
    private String degree;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(name = "shown_publicly", nullable = false)
    private boolean shownPublicly;
}
