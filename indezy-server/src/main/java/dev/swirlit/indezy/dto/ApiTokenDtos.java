package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ApiTokenScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Personal API tokens: as listed, as asked for, and as created (the only time the token itself is returned). */
public final class ApiTokenDtos {

    private ApiTokenDtos() {
    }

    /** A token as listed; no scopes means it reads the whole workspace. */
    public record Token(Long id, String name, String tokenStart, LocalDateTime createdAt, LocalDateTime lastUsedAt,
                        LocalDateTime expiresAt, List<ApiTokenScope> scopes) {
        public Token {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }

        @Override
        public String toString() {
            return "Token[id=" + id + "]";
        }
    }

    /**
     * A name, the days it lasts (30, 90 or 365, or none for a token that does not expire), and the parts of the workspace it
     * reads (none for all of it).
     */
    public record Create(@NotBlank @Size(max = 60) String name, Integer expiresInDays, Set<@NotNull ApiTokenScope> scopes) {
        public Create {
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        }
    }

    /** Who a usable token signs in, and what it may read; no scopes means all of the workspace. */
    public record Authenticated(Long userId, Set<ApiTokenScope> scopes) {
        public Authenticated {
            scopes = Set.copyOf(scopes);
        }
    }

    public record Created(Token token, String secret) {
        @Override
        public String toString() {
            return "Created[token=" + (token == null ? null : token.id()) + "]";
        }
    }
}
