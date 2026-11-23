package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DocumentTemplateKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A Word template the freelancer laid out for their invoices or their quotes (see docs/features.md#invoices), filled
 * with each one's values for its Word copy. One per kind and workspace: uploading another replaces it.
 */
@Entity
@Table(name = "document_templates", uniqueConstraints = @UniqueConstraint(name = "uk_document_templates_kind",
    columnNames = {"freelance_id", "kind"}))
@Getter
@Setter
public class DocumentTemplate extends BaseEntity {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private DocumentTemplateKind kind;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = MAX_BYTES)
    private byte[] content;
}
