package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email address
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Find user with sessions loaded
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.sessions WHERE u.id = :id")
    Optional<User> findByIdWithSessions(@Param("id") Long id);

    /**
     * Find user with security questions loaded
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.securityQuestions WHERE u.id = :id")
    Optional<User> findByIdWithSecurityQuestions(@Param("id") Long id);

    /**
     * Find user with all security-related data loaded
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.sessions LEFT JOIN FETCH u.securityQuestions WHERE u.id = :id")
    Optional<User> findByIdWithSecurityData(@Param("id") Long id);
}
