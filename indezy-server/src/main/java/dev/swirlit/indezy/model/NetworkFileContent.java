package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The bytes of a {@link NetworkFile}: the file as served, and a document's picture of its first page. */
@Entity
@Table(name = "network_file_contents")
@Getter
@Setter
public class NetworkFileContent {

    @Id
    @Column(name = "file_id")
    private Long fileId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = NetworkFile.MAX_BYTES)
    private byte[] content;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "preview", length = NetworkFile.MAX_BYTES)
    private byte[] preview;
}
