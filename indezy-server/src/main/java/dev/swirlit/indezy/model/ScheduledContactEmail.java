package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * An email to a contact waiting for its time to go from Indezy (an announcement spread over working days): sent then,
 * recorded in the contact's exchanges, and dropped instead when the contact was in touch since or asked not to be
 * contacted. It goes with its contact.
 */
@Entity
@Table(name = "scheduled_contact_emails", indexes = {
    @Index(name = "idx_scheduled_contact_emails_send", columnList = "send_at"),
    @Index(name = "idx_scheduled_contact_emails_freelance", columnList = "freelance_id")})
@Getter
@Setter
public class ScheduledContactEmail extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    /** Whose profile's dossier goes attached. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Contact contact;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "dossier", nullable = false)
    private boolean dossier;

    /**
     * A campaign's reminder: sent only when the campaign's first email to the contact went and nothing but the
     * campaign's own emails was recorded with them since it was planned; null or false for any other email.
     */
    @Column(name = "is_reminder")
    private Boolean reminder;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 2)
    private DocumentLanguage language;

    /** The campaign it goes in; null for none. */
    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "send_at", nullable = false)
    private Instant sendAt;

    /** When a replica took it to send; null while it waits. */
    @Column(name = "claimed_at")
    private Instant claimedAt;

    /** Failed tries; it is dropped after a few. */
    @Column(name = "attempts", nullable = false)
    private int attempts;
}
