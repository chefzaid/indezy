package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The CV the freelancer keeps in their profile (see docs/features.md#profile-import), a PDF, Word or OpenDocument file
 * checked for malware, ready to download and attach to applications. One per workspace: keeping another replaces it.
 */
@Entity
@Table(name = "profile_cvs")
@Getter
@Setter
public class ProfileCv extends BaseEntity {

    public static final int MAX_BYTES = 10 * 1024 * 1024;

    @Column(name = "freelance_id", nullable = false, unique = true)
    private Long freelanceId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = MAX_BYTES)
    private byte[] content;
}
