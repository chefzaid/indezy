package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

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

    /** Works remotely: the card says so beside, or instead of, the city. */
    @Column(name = "remote", nullable = false)
    private boolean remote;
}
