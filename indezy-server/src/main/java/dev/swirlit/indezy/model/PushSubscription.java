package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A browser of an account that accepted web push: where to send, and the keys to encrypt for it (RFC 8291). */
@Entity
@Table(name = "push_subscriptions", indexes = @Index(name = "idx_push_subscriptions_user", columnList = "user_id"))
@Getter
@Setter
public class PushSubscription extends BaseEntity {

    public static final int ENDPOINT_MAX = 1000;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "endpoint", nullable = false, unique = true, length = ENDPOINT_MAX)
    private String endpoint;

    /** The browser's P-256 public key, base64url. */
    @Column(name = "p256dh", nullable = false, length = 120)
    private String p256dh;

    /** The browser's 16-byte authentication secret, base64url. */
    @Column(name = "auth", nullable = false, length = 40)
    private String auth;

    // The keys stay out of logs.
    @Override
    public String toString() {
        return "PushSubscription[id=" + getId() + ", userId=" + userId + "]";
    }
}
