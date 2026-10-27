package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ComplianceDocumentType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * A document an ESN asks of a freelancer before and during a mission (URSSAF vigilance certificate, company
 * registration extract, professional insurance, bank details, identity), stored in the database with the type read
 * from its content. Listing reads everything but the content (see {@code ComplianceDocumentRepository}).
 */
@Entity
@Table(name = "compliance_documents")
@Getter
@Setter
public class ComplianceDocument extends BaseEntity {

    public static final int MAX_BYTES = 10 * 1024 * 1024;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private ComplianceDocumentType type;

    /** What the document is, in the freelancer's words; required for {@code OTHER}. */
    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "file_name", nullable = false, length = ProjectAttachment.NAME_MAX)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = MAX_BYTES)
    private byte[] content;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    /** When it stops being valid; empty for a document that does not expire, such as bank details. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;
}
