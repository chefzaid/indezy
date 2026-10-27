package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.CompliancePack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompliancePackRepository extends JpaRepository<CompliancePack, Long> {

    List<CompliancePack> findByFreelanceIdOrderByCreatedAtDesc(Long freelanceId);

    Optional<CompliancePack> findByIdAndFreelanceId(Long id, Long freelanceId);

    Optional<CompliancePack> findByTokenHash(String tokenHash);

    /** The packs that share a document, to forget it once deleted. */
    @Query("SELECT p FROM CompliancePack p WHERE :documentId MEMBER OF p.documentIds")
    List<CompliancePack> findSharing(@Param("documentId") Long documentId);
}
