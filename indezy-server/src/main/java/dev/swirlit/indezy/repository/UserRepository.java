package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email address
     */
    Optional<User> findByEmail(String email);

    /** Whether the account may use the app: neither deleted nor disabled. */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.id = :id AND u.deletedAt IS NULL AND u.disabledAt IS NULL")
    boolean isActive(@Param("id") Long id);

    /** Accounts whose email or name holds the text, case aside, newest first. */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) " +
        "OR LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE LOWER(CONCAT('%', :text, '%'))")
    Page<User> search(@Param("text") String text, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime after);

    long countByDisabledAtIsNotNull();

    /** The account whose public profile has this address name, shown or not. */
    Optional<User> findByPublicProfileSlug(String slug);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);
}
