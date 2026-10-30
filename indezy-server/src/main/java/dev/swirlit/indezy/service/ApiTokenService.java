package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ApiTokenDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ApiToken;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.repository.ApiTokenRepository;
import dev.swirlit.indezy.util.Tokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Personal API tokens: read-only access to the workspace for another tool. A token is 32 random bytes after the
 * {@code idz_} prefix, returned once; only its SHA-256 hash is stored, so a database copy cannot be used to call the API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiTokenService {

    public static final String PREFIX = "idz_";
    static final int MAX_TOKENS = 10;
    static final Set<Integer> LIFETIMES = Set.of(30, 90, 365);
    private static final Duration USED_EVERY = Duration.ofMinutes(5);
    private static final int TOKEN_START_LENGTH = 12;

    private final ApiTokenRepository tokens;
    private final AccountEventService events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ApiTokenDtos.Token> list(Long userId) {
        return tokens.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId).stream().map(ApiTokenService::toDto).toList();
    }

    @Transactional
    public ApiTokenDtos.Created create(Long userId, ApiTokenDtos.Create request) {
        Integer days = request.expiresInDays();
        if (days != null && !LIFETIMES.contains(days)) {
            throw new IllegalArgumentException("A token lasts 30, 90 or 365 days, or does not expire");
        }
        if (tokens.countByUserIdAndRevokedAtIsNull(userId) >= MAX_TOKENS) {
            throw new InvalidStateException("An account has " + MAX_TOKENS + " API tokens at most: revoke one first");
        }
        String secret = PREFIX + Tokens.random();
        ApiToken token = new ApiToken();
        token.setUserId(userId);
        token.setName(request.name().strip());
        token.setTokenHash(Tokens.sha256Hex(secret));
        token.setTokenStart(secret.substring(0, TOKEN_START_LENGTH));
        token.setExpiresAt(days == null ? null : LocalDateTime.now(clock).plusDays(days));
        token.setScopes(request.scopes());
        tokens.save(token);
        events.record(userId, AccountEventType.API_TOKEN_CREATED, token.getName());
        log.info("Account {} created API token {}", userId, token.getId());
        return new ApiTokenDtos.Created(toDto(token), secret);
    }

    @Transactional
    public void revoke(Long userId, Long tokenId) {
        ApiToken token = tokens.findByIdAndUserIdAndRevokedAtIsNull(tokenId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("API token not found"));
        token.setRevokedAt(LocalDateTime.now(clock));
        events.record(userId, AccountEventType.API_TOKEN_REVOKED, token.getName());
        log.info("Account {} revoked API token {}", userId, tokenId);
    }

    /** The account a usable token belongs to, its last use noted every 5 minutes at most. */
    @Transactional
    public Optional<ApiTokenDtos.Authenticated> authenticate(String secret) {
        if (secret == null || !secret.startsWith(PREFIX)) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return tokens.findUsable(Tokens.sha256Hex(secret), now).map(token -> {
            if (token.getLastUsedAt() == null || token.getLastUsedAt().isBefore(now.minus(USED_EVERY))) {
                tokens.touch(token.getId(), now);
            }
            return new ApiTokenDtos.Authenticated(token.getUserId(), token.getScopes());
        });
    }

    private static ApiTokenDtos.Token toDto(ApiToken token) {
        return new ApiTokenDtos.Token(token.getId(), token.getName(), token.getTokenStart(), token.getCreatedAt(),
            token.getLastUsedAt(), token.getExpiresAt(), token.getScopes().stream().sorted().toList());
    }
}
