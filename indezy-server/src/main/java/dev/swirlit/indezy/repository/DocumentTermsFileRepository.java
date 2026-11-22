package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.DocumentTermsFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentTermsFileRepository extends JpaRepository<DocumentTermsFile, Long> {

    Optional<DocumentTermsFile> findByIdAndFreelanceId(Long id, Long freelanceId);
}
