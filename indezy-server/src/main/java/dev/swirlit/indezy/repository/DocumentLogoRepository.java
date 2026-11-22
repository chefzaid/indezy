package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.DocumentLogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentLogoRepository extends JpaRepository<DocumentLogo, Long> {

    Optional<DocumentLogo> findByIdAndFreelanceId(Long id, Long freelanceId);
}
