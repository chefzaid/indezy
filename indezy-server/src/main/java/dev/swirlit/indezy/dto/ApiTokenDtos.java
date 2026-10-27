package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Personal API tokens: as listed, as asked for, and as created (the only time the token itself is returned). */
public final class ApiTokenDtos {

    private ApiTokenDtos() {
    }

    public record Token(Long id, String name, String tokenStart, LocalDateTime createdAt, LocalDateTime lastUsedAt,
                        LocalDateTime expiresAt) {
        @Override
        public String toString() {
            return "Token[id=" + id + "]";
        }
    }

    /** A name, and the days it lasts: 30, 90 or 365, or none for a token that does not expire. */
    public record Create(@NotBlank @Size(max = 60) String name, Integer expiresInDays) {
    }

    public record Created(Token token, String secret) {
        @Override
        public String toString() {
            return "Created[token=" + (token == null ? null : token.id()) + "]";
        }
    }
}
