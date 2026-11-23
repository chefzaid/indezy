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
 * A TrueType font uploaded for invoices and quotes (see docs/features.md#billing-identity), its regular face and, when
 * given, its bold one, embedded in the PDFs. Never changed once stored: a new upload is a new row, so the documents issued
 * with the previous one keep printing in it. It goes with its workspace.
 */
@Entity
@Table(name = "document_font_files", indexes = @Index(name = "idx_document_font_files_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class DocumentFontFile extends BaseEntity {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    /** The family the font names itself by, for the Word copies. */
    @Column(name = "family", nullable = false, length = 100)
    private String family;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "regular", nullable = false, length = MAX_BYTES)
    private byte[] regular;

    /** The bold face; null when none was given, the regular one then printing the bold text. */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "bold", length = MAX_BYTES)
    private byte[] bold;
}
