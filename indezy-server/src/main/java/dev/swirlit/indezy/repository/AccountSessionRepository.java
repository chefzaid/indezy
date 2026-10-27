package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.AccountSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountSessionRepository extends JpaRepository<AccountSession, Long> {

    /** The session a token names, while it is open and its account is active. */
    @Query("SELECT s FROM AccountSession s, User u WHERE u.id = s.userId AND s.sessionKey = :key AND s.userId = :userId " +
        "AND s.revokedAt IS NULL AND s.expiresAt > :now AND u.deletedAt IS NULL AND u.disabledAt IS NULL")
    Optional<AccountSession> findOpen(@Param("key") String key, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE AccountSession s SET s.lastSeenAt = :now WHERE s.id = :id")
    void touch(@Param("id") Long id, @Param("now") LocalDateTime now);

    List<AccountSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, LocalDateTime now);

    Optional<AccountSession> findByIdAndUserId(Long id, Long userId);

    /** Ends every open session of the account but {@code keepId} (-1 keeps none); answers how many ended. */
    @Modifying
    @Query("UPDATE AccountSession s SET s.revokedAt = :now WHERE s.userId = :userId AND s.revokedAt IS NULL " +
        "AND s.expiresAt > :now AND s.id <> :keepId")
    int revokeAll(@Param("userId") Long userId, @Param("keepId") Long keepId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM AccountSession s WHERE s.expiresAt < :before OR s.revokedAt < :before")
    int deleteEndedBefore(@Param("before") LocalDateTime before);
}
