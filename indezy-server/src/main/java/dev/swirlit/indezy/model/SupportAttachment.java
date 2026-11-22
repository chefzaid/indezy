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
 * A screenshot or a PDF a member added to their request to support (see docs/features.md#support-requests): images
 * drawn anew, PDFs checked for malware. It goes with its request.
 */
@Entity
@Table(name = "support_attachments", indexes = @Index(name = "idx_support_attachments_request", columnList = "request_id"))
@Getter
@Setter
public class SupportAttachment extends BaseEntity {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    /** At most this many files on a request. */
    public static final int PER_REQUEST = 3;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = MAX_BYTES)
    private byte[] content;

    // The file is the member's: kept out of logs.
    @Override
    public String toString() {
        return "SupportAttachment[id=" + getId() + ", request=" + requestId + "]";
    }
}
