package dev.swirlit.indezy.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A file kept with an opportunity (job description, contract, technical test brief), stored in the database with its
 * type as read from its content. Listing reads only its name, type and size (see {@code ProjectAttachmentRepository}).
 */
@Entity
@Table(name = "project_attachments")
@Getter
@Setter
public class ProjectAttachment extends BaseEntity {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int NAME_MAX = 255;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "file_name", nullable = false, length = NAME_MAX)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = MAX_BYTES)
    private byte[] content;
}
