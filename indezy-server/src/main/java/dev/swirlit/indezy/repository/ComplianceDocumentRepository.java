package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ComplianceDocument;
import dev.swirlit.indezy.model.enums.ComplianceDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplianceDocumentRepository extends JpaRepository<ComplianceDocument, Long> {

    /** What a document is, without reading its content. */
    interface Summary {
        Long getId();

        ComplianceDocumentType getType();

        String getLabel();

        String getFileName();

        String getContentType();

        long getSizeBytes();

        LocalDate getIssuedOn();

        LocalDate getExpiresOn();

        LocalDateTime getCreatedAt();
    }

    @Query("SELECT d.id AS id, d.type AS type, d.label AS label, d.fileName AS fileName, d.contentType AS contentType, " +
        "d.sizeBytes AS sizeBytes, d.issuedOn AS issuedOn, d.expiresOn AS expiresOn, d.createdAt AS createdAt " +
        "FROM ComplianceDocument d WHERE d.freelanceId = :freelanceId ORDER BY d.id")
    List<Summary> findSummaries(@Param("freelanceId") Long freelanceId);

    @Query("SELECT d.id AS id, d.type AS type, d.label AS label, d.fileName AS fileName, d.contentType AS contentType, " +
        "d.sizeBytes AS sizeBytes, d.issuedOn AS issuedOn, d.expiresOn AS expiresOn, d.createdAt AS createdAt " +
        "FROM ComplianceDocument d WHERE d.freelanceId = :freelanceId AND d.id IN :ids ORDER BY d.id")
    List<Summary> findSummaries(@Param("freelanceId") Long freelanceId, @Param("ids") Collection<Long> ids);

    /** The workspace's documents that expire by the given day, the soonest first. */
    @Query("SELECT d.id AS id, d.type AS type, d.label AS label, d.fileName AS fileName, d.contentType AS contentType, " +
        "d.sizeBytes AS sizeBytes, d.issuedOn AS issuedOn, d.expiresOn AS expiresOn, d.createdAt AS createdAt " +
        "FROM ComplianceDocument d WHERE d.freelanceId = :freelanceId AND d.expiresOn <= :by ORDER BY d.expiresOn, d.id")
    List<Summary> findExpiringBy(@Param("freelanceId") Long freelanceId, @Param("by") LocalDate by);

    Optional<ComplianceDocument> findByIdAndFreelanceId(Long id, Long freelanceId);

    long countByFreelanceId(Long freelanceId);
}
