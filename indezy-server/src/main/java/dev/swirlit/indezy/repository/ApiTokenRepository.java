package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    /** The token with this hash, while it is neither revoked nor expired and its account is active. */
    @Query("SELECT t FROM ApiToken t, User u WHERE u.id = t.userId AND t.tokenHash = :hash AND t.revokedAt IS NULL " +
        "AND (t.expiresAt IS NULL OR t.expiresAt > :now) AND u.deletedAt IS NULL AND u.disabledAt IS NULL")
    Optional<ApiToken> findUsable(@Param("hash") String hash, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE ApiToken t SET t.lastUsedAt = :now WHERE t.id = :id")
    void touch(@Param("id") Long id, @Param("now") LocalDateTime now);

    List<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndRevokedAtIsNull(Long userId);

    Optional<ApiToken> findByIdAndUserIdAndRevokedAtIsNull(Long id, Long userId);
}
