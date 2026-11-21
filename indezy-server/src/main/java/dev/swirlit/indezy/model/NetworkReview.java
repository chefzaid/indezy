package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkReviewKind;
import dev.swirlit.indezy.model.enums.NetworkReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * A member's review of a client or an ESN they worked with: three ratings out of five and a text, shown without its
 * author once the filter or an administrator let it through. One per member and company, the company known by its SIREN,
 * else by its name, so the reviews of members who each keep it as their own client meet.
 */
@Entity
@Table(name = "network_reviews",
    indexes = @Index(name = "idx_network_reviews_company", columnList = "company_key, status"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_reviews_author_company", columnNames = {"author_id", "company_key"}))
@Getter
@Setter
public class NetworkReview extends BaseEntity {

    public static final int TEXT_MAX = 2000;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /** "siren:552100554", or "name:" and the name folded, without a SIREN. */
    @Column(name = "company_key", nullable = false, length = 140)
    private String companyKey;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    /** The author's own client the review was written from. */
    @Column(name = "client_id")
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private NetworkReviewKind kind;

    /** How promptly it paid, 1 to 5. */
    @Column(name = "payment_rating", nullable = false)
    private Integer paymentRating;

    /** How its hiring process went, 1 to 5. */
    @Column(name = "process_rating", nullable = false)
    private Integer processRating;

    /** How honest it was about the rate and the mission, 1 to 5. */
    @Column(name = "rate_rating", nullable = false)
    private Integer rateRating;

    @Column(name = "text", nullable = false, length = TEXT_MAX)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private NetworkReviewStatus status;

    /** Why the filter held it, comma-separated ({@code PHONE,CONTACT_NAME}); none when it went out. */
    @Column(name = "held_reasons", length = 120)
    private String heldReasons;
}
