package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email address
     */
    Optional<User> findByEmail(String email);

    /** The account whose public profile has this address name, shown or not. */
    Optional<User> findByPublicProfileSlug(String slug);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);
}
