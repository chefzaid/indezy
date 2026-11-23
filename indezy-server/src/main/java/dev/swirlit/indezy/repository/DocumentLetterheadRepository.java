package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.DocumentLetterhead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentLetterheadRepository extends JpaRepository<DocumentLetterhead, Long> {

    Optional<DocumentLetterhead> findByIdAndFreelanceId(Long id, Long freelanceId);
}
