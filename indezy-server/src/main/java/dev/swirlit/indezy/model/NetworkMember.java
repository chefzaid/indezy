package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkAvailability;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An account's membership of the member network: its handle, unique among members, and what its member card shows of
 * the profile. Leaving keeps the row, inactive, so the handle stays the member's and coming back restores the card.
 */
@Entity
@Table(name = "network_members")
@Getter
@Setter
public class NetworkMember extends BaseEntity {

    public static final int HANDLE_MAX = 30;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** Lower-case letters, digits and underscores, starting with a letter ({@code camille_d}). */
    @Column(name = "handle", nullable = false, unique = true, length = HANDLE_MAX)
    private String handle;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "show_photo", nullable = false)
    private boolean showPhoto = true;

    @Column(name = "show_headline", nullable = false)
    private boolean showHeadline = true;

    @Column(name = "show_stack", nullable = false)
    private boolean showStack = true;

    @Column(name = "show_location", nullable = false)
    private boolean showLocation = true;

    @Column(name = "show_availability", nullable = false)
    private boolean showAvailability = true;

    @Column(name = "show_languages", nullable = false)
    private boolean showLanguages = true;

    /** How the card tells the availability, and the day it names (from when available, until when on a mission). */
    @Enumerated(EnumType.STRING)
    @Column(name = "availability_mode", length = 20)
    private NetworkAvailability availabilityMode = NetworkAvailability.AUTO;

    @Column(name = "availability_date")
    private LocalDate availabilityDate;

    /**
     * The SIRET the registry confirmed as the member's own business, and when: the card shows the verified badge while
     * the billing identity keeps that SIRET.
     */
    @Column(name = "verified_siret", length = 14)
    private String verifiedSiret;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** The kinds of notification the member turned off, comma-separated ({@code LIKE,REPOST}); none when empty. */
    @Column(name = "notifications_off", length = 100)
    private String notificationsOff;

    /** The kinds of notification also emailed, comma-separated; none (null or empty) by default. */
    @Column(name = "notifications_emailed", length = 100)
    private String notificationsEmailed;

    /** When the latest notifications email was sent, so at most one goes every three hours. */
    @Column(name = "notification_email_sent_at")
    private Instant notificationEmailSentAt;

    /** Works remotely: the card says so beside, or instead of, the city. */
    @Column(name = "remote", nullable = false)
    private boolean remote;

    /** Until then, the moderators keep the member from posting, commenting, reposting, following and reporting. */
    @Column(name = "suspended_until")
    private Instant suspendedUntil;

    /** When the moderators ended the membership: the account cannot join again. */
    @Column(name = "banned_at")
    private Instant bannedAt;

    /** Only the followers the member approves see their posts; none (null) reads as public. */
    @Column(name = "private_card")
    private Boolean privateCard;

    /**
     * Whether members who went through the same ESNs (both had an opportunity with it) are suggested to follow, both ways;
     * none (null) reads as off: only between members who both turn it on.
     */
    @Column(name = "esn_suggestions")
    private Boolean esnSuggestions;

    /** Whether the members the member writes to see when they read a message, and the member when they did; none reads as on. */
    @Column(name = "read_receipts")
    private Boolean readReceipts;

    /** Whether the members the member writes to see them typing, and the member them; none reads as on. */
    @Column(name = "typing_shown")
    private Boolean typingShown;

    /** Whether the members the member writes to see when they are online, and the member when they are; none reads as on. */
    @Column(name = "presence_shown")
    private Boolean presenceShown;

    /** The last time the member had the app open (a live stream on some replica), at most a heartbeat ago while they do. */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public boolean showsPresence() {
        return !Boolean.FALSE.equals(presenceShown);
    }

    public boolean showsReadReceipts() {
        return !Boolean.FALSE.equals(readReceipts);
    }

    public boolean showsTyping() {
        return !Boolean.FALSE.equals(typingShown);
    }

    public boolean isEsnSuggestions() {
        return Boolean.TRUE.equals(esnSuggestions);
    }

    public boolean isPrivate() {
        return Boolean.TRUE.equals(privateCard);
    }
}
