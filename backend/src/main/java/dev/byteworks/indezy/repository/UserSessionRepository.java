package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /**
     * Find session by session ID
     */
    Optional<UserSession> findBySessionId(String sessionId);

    /**
     * Find all sessions for a user
     */
    List<UserSession> findByUserIdOrderByLastActiveDesc(Long userId);

    /**
     * Find active sessions for a user (last active within 24 hours)
     */
    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.lastActive > :cutoffTime ORDER BY s.lastActive DESC")
    List<UserSession> findActiveSessionsByUserId(@Param("userId") Long userId, @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Delete session by session ID
     */
    void deleteBySessionId(String sessionId);

    /**
     * Delete all sessions for a user except the current one
     */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.user.id = :userId AND s.sessionId != :currentSessionId")
    void deleteAllUserSessionsExceptCurrent(@Param("userId") Long userId, @Param("currentSessionId") String currentSessionId);

    /**
     * Update last active time for a session
     */
    @Modifying
    @Query("UPDATE UserSession s SET s.lastActive = :lastActive WHERE s.sessionId = :sessionId")
    void updateLastActive(@Param("sessionId") String sessionId, @Param("lastActive") LocalDateTime lastActive);
}
