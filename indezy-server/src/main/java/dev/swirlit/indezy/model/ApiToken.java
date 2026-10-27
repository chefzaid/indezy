package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import dev.swirlit.indezy.model.enums.ApiTokenScope;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

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

    /** The parts of the workspace the token reads; none for all of it. */
    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_token_scopes", joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "scope", nullable = false, length = 30)
    private Set<ApiTokenScope> scopes = EnumSet.noneOf(ApiTokenScope.class);

    public Set<ApiTokenScope> getScopes() {
        return scopes.isEmpty() ? Set.of() : EnumSet.copyOf(scopes);
    }

    public void setScopes(Set<ApiTokenScope> scopes) {
        this.scopes = scopes.isEmpty() ? EnumSet.noneOf(ApiTokenScope.class) : EnumSet.copyOf(scopes);
    }
}
