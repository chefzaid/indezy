package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A personal token another tool uses to read the workspace. Only its SHA-256 hash is kept: the token itself is shown
 * once, when created.
 */
@Entity
@Table(name = "api_tokens", indexes = @Index(name = "idx_api_tokens_user", columnList = "user_id"))
@Getter
@Setter
public class ApiToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** The token's first characters, to tell it apart in the list. */
    @Column(name = "token_start", nullable = false, length = 12)
    private String tokenStart;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
