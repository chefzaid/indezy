package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.SignInMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One sign-in: the token it issued names it by {@link #sessionKey}, and stops working once it is revoked or expired.
 * The device is a browser and system name read from the User-Agent; no address is kept.
 */
@Entity
@Table(name = "account_sessions", indexes = @Index(name = "idx_account_sessions_user", columnList = "user_id"))
@Getter
@Setter
public class AccountSession extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_key", nullable = false, unique = true, length = 36)
    private String sessionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private SignInMethod method;

    @Column(name = "device", length = 80)
    private String device;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
