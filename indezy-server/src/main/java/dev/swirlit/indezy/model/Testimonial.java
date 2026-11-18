package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.TestimonialStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A recommendation a client contact writes through a link, without an account (see docs/features.md#testimonials): asked
 * by the freelancer, written once by its author who agrees to be named, then shown on the public profile once approved.
 */
@Entity
@Table(name = "testimonials")
@Getter
@Setter
public class Testimonial extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    /** The contact asked, when picked among the workspace's contacts. */
    @Column(name = "contact_id")
    private Long contactId;

    /** Who the link was sent to, as the freelancer named them. */
    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    /** Where the link was emailed: the external person's address, or the contact's; none when only the link was given. */
    @Column(name = "recipient_email", length = 254)
    private String recipientEmail;

    @Column(name = "token_hash", length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TestimonialStatus status = TestimonialStatus.REQUESTED;

    @Column(name = "author_name", length = 120)
    private String authorName;

    /** The author's title and company, as they wrote it ("CTO, Acme"). */
    @Column(name = "author_title", length = 160)
    private String authorTitle;

    @Column(name = "text", length = 1500)
    private String text;

    @Column(name = "received_at")
    private Instant receivedAt;
}
