package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A letterhead uploaded as a PDF (see docs/features.md#billing-identity): one or two A4 pages that invoices and quotes are
 * drawn over, the first page's on the first page of a document and the second's (or the first again) on the others.
 * Never changed once stored: issued documents keep the one they were issued with. It goes with its workspace.
 */
@Entity
@Table(name = "document_letterheads", indexes = @Index(name = "idx_document_letterheads_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class DocumentLetterhead extends BaseEntity {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_PAGES = 2;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "pages", nullable = false)
    private int pages;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = MAX_BYTES)
    private byte[] content;
}
