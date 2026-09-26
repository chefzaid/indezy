package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Freelance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FreelanceRepository extends JpaRepository<Freelance, Long> {

    Optional<Freelance> findByEmail(String email);

    boolean existsByEmail(String email);

}
