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
 * Terms and conditions of sale uploaded as a PDF (see docs/features.md#quotes), appended after each quote. Never changed
 * once stored: a new upload is a new row, so quotes sent with an older one, which keep its id, still append it. It goes
 * with its workspace.
 */
@Entity
@Table(name = "document_terms_files", indexes = @Index(name = "idx_document_terms_files_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class DocumentTermsFile extends BaseEntity {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    /** A PDF of more pages is refused: terms and conditions run a few pages. */
    public static final int MAX_PAGES = 20;

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
