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
 * A logo printed on a workspace's invoices and quotes (see docs/features.md#billing-identity), drawn anew at most
 * {@value #LONGEST_SIDE} pixels wide or high. Never changed once stored: a new logo is a new row, so the documents issued
 * with an older one, which keep its id, still print it. It goes with its workspace.
 */
@Entity
@Table(name = "document_logos", indexes = @Index(name = "idx_document_logos_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class DocumentLogo extends BaseEntity {

    public static final int UPLOAD_MAX_BYTES = 2 * 1024 * 1024;
    public static final int LONGEST_SIDE = 600;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = UPLOAD_MAX_BYTES)
    private byte[] content;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;
}
