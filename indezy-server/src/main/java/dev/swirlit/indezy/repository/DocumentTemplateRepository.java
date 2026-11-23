package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.DocumentTemplate;
import dev.swirlit.indezy.model.enums.DocumentTemplateKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {

    Optional<DocumentTemplate> findByFreelanceIdAndKind(Long freelanceId, DocumentTemplateKind kind);

    List<DocumentTemplate> findByFreelanceId(Long freelanceId);
}
