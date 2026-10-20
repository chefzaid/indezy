package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import dev.swirlit.indezy.util.TextFolding;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** What one workspace did with a job offer: saved it, hid it, saw it or imported it into its pipeline. */
@Entity
@Table(name = "job_offer_states",
    uniqueConstraints = @UniqueConstraint(name = "uk_job_offer_states_freelance_offer", columnNames = {"freelance_id", "offer_id"}))
@Getter
@Setter
public class JobOfferState extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", nullable = false)
    private JobOffer offer;

    @Column(name = "saved", nullable = false)
    private boolean saved;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "seen_at")
    private Instant seenAt;

    /** The workspace's private note on the offer (kept on each copy of the mission). */
    @Setter(AccessLevel.NONE)
    @Column(name = "note", length = OfferNotes.MAX_LENGTH)
    private String note;

    /** The note's words folded, for the keyword filter. */
    @Setter(AccessLevel.NONE)
    @Column(name = "note_key", length = OfferNotes.MAX_LENGTH)
    private String noteKey;

    /** The opportunity created from the offer; cleared when that project is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_project_id")
    private Project importedProject;

    /** Sets the note, stripped; a blank one clears it. */
    public void setNote(String note) {
        this.note = note == null || note.isBlank() ? null : note.strip();
        this.noteKey = this.note == null ? null : TextFolding.words(this.note);
    }
}
