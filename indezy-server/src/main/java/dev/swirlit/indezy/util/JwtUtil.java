package dev.swirlit.indezy.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    private static final String SESSION_CLAIM = "sid";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Instant extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration).toInstant();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).isBefore(Instant.now());
    }

    /** A token for the account's session {@code sessionKey}, which must stay open for the token to work. */
    public String generateToken(String username, Long userId, String sessionKey) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put(SESSION_CLAIM, sessionKey);
        return createToken(claims, username);
    }

    /** How long a token lasts. */
    public Duration lifetime() {
        return Duration.ofMillis(expiration);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Instant issuedAt = Instant.now();
        // JJWT 0.12 accepts Date at its API boundary; all time arithmetic uses Instant.
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(expiration)))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Long.class);
    }

    /** The session the token belongs to; null for a token issued before sessions were kept. */
    public String extractSessionKey(String token) {
        return extractAllClaims(token).get(SESSION_CLAIM, String.class);
    }
}
