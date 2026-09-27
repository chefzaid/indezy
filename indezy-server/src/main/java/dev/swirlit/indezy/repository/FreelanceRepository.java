package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Freelance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FreelanceRepository extends JpaRepository<Freelance, Long> {

    Optional<Freelance> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Account and workspace ownership use the same email link as AccessGuard. */
    @Query("SELECT u.inactivityDays FROM User u, Freelance f WHERE f.id = :freelanceId AND u.email = f.email")
    Optional<Integer> findInactivityDays(@Param("freelanceId") Long freelanceId);

}
