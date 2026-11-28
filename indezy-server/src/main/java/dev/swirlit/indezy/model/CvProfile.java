package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A CV profile (see docs/features.md#cv-profiles): one positioning of the same person ("Java / Cloud backend", "Tech
 * Lead"), holding only wording and choices on top of the profile: a headline, a summary, which skills show and in what
 * order, which won missions and past positions show, in what order and with what wording. The facts (dates, clients,
 * stacks, roles) stay the profile's, read when the dossier is drawn. The choices are kept as JSON text; null: as the
 * profile says.
 */
@Entity
@Table(name = "cv_profiles", indexes = @Index(name = "idx_cv_profiles_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class CvProfile extends BaseEntity {

    public static final int NAME_MAX = 80;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    /** The profile drawn wherever no other is chosen, e.g. the dossier download. */
    @Column(name = "default_profile", nullable = false)
    private boolean defaultProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    private DocumentLanguage language = DocumentLanguage.FR;

    @Column(name = "headline", length = 120)
    private String headline;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** The skill names shown, in order, as JSON; null for all of the profile's. */
    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    /** The won missions shown, in order, each {@code {id, text}}, as JSON; null for all of them as they are. */
    @Column(name = "missions", columnDefinition = "TEXT")
    private String missions;

    /** The same profile in the other language, kept in step by {@code Translate (AI)}; none without one. */
    @Column(name = "twin_id")
    private Long twinId;

    /**
     * The facts the wording relied on when it was last saved or reviewed, as JSON: the profile's skills and certifications,
     * each mission's and position's title, dates and stack; compared with the profile's to flag the profile to review.
     */
    @Column(name = "facts", columnDefinition = "TEXT")
    private String facts;

    /** The past positions shown, as {@link #missions} are. */
    @Column(name = "positions", columnDefinition = "TEXT")
    private String positions;
}
