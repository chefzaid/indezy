package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkIdeaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** An idea for Indezy a member proposed on the ideas board, which members vote for and the administrators answer. */
@Entity
@Table(name = "network_ideas")
@Getter
@Setter
public class NetworkIdea extends BaseEntity {

    public static final int TITLE_MIN = 5;
    public static final int TITLE_MAX = 120;
    public static final int DESCRIPTION_MAX = 2000;
    public static final int NOTE_MAX = 500;

    /** The member who proposed it; none once their account is deleted, the idea staying for those who voted for it. */
    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "title", nullable = false, length = TITLE_MAX)
    private String title;

    @Column(name = "description", length = DESCRIPTION_MAX)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NetworkIdeaStatus status;

    /** The administrators' word on it: why it is declined, when it is planned... */
    @Column(name = "status_note", length = NOTE_MAX)
    private String statusNote;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    /** The idea it repeats, once merged into it. */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;
}
