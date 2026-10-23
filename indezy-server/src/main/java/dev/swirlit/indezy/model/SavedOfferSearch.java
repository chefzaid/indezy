package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Filters of the offer feed a workspace saved under a name, to come back to them and count what is new since, and
 * optionally receive the offers Indezy finds for them by email.
 */
@Entity
@Table(name = "saved_offer_searches",
    uniqueConstraints = @UniqueConstraint(name = "uk_saved_offer_searches_freelance_name", columnNames = {"freelance_id", "name"}))
@Getter
@Setter
public class SavedOfferSearch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** The feed's filters and sort, as JSON. */
    @Column(name = "filters", nullable = false, columnDefinition = "TEXT")
    private String filters;

    /** When the workspace last opened the search: offers Indezy found after it are new. */
    @Column(name = "last_opened_at", nullable = false)
    private Instant lastOpenedAt;

    /** How often the search emails its offers; {@code null}, for a search saved before emails, is {@code OFF}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "email_frequency", length = 10)
    private SavedSearchEmails emailFrequency;

    /** The hour of a daily email, Paris time. */
    @Column(name = "email_hour")
    private Integer emailHour;

    /** The language the emails are written in: the one the workspace used when it turned them on. */
    @Enumerated(EnumType.STRING)
    @Column(name = "email_language", length = 2)
    private DocumentLanguage emailLanguage;

    /** Offers Indezy found after it are the next email's. */
    @Column(name = "emailed_until")
    private Instant emailedUntil;

    /**
     * When the first email was sent since an offer was last opened from one ({@code null} once one is opened): sending
     * stops 60 days after it.
     */
    @Column(name = "unopened_since")
    private Instant unopenedSince;

    /** When sending stopped because no offer was opened from 60 days of emails. */
    @Column(name = "emails_stopped_at")
    private Instant emailsStoppedAt;

    /** The token of the email's unsubscribe link, which can only stop this search's emails. */
    @Column(name = "email_token", length = 43, unique = true)
    private String emailToken;

    public SavedSearchEmails getEmailFrequency() {
        return emailFrequency == null ? SavedSearchEmails.OFF : emailFrequency;
    }
}
