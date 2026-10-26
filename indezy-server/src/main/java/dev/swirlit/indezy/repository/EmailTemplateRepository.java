package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    List<EmailTemplate> findByFreelanceIdOrderByNameAsc(Long freelanceId);

    Optional<EmailTemplate> findByIdAndFreelanceId(Long id, Long freelanceId);

    long countByFreelanceId(Long freelanceId);

    boolean existsByFreelanceIdAndNameIgnoreCaseAndIdNot(Long freelanceId, String name, Long id);
}
