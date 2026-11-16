package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkFileKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An image or a document a member added to a post or a message (see docs/features.md#member-network): uploaded first,
 * then attached when the post or the message goes out (or kept with a post held for review); its content is in
 * {@link NetworkFileContent}, read only to serve it.
 */
@Entity
@Table(name = "network_files", indexes = {
    @Index(name = "idx_network_files_post", columnList = "post_id"),
    @Index(name = "idx_network_files_message", columnList = "message_id"),
    @Index(name = "idx_network_files_owner", columnList = "owner_id")
})
@Getter
@Setter
public class NetworkFile extends BaseEntity {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int NAME_MAX = 255;
    public static final int ALT_MAX = 300;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private NetworkFileKind kind;

    @Column(name = "file_name", nullable = false, length = NAME_MAX)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** What an image shows, for those who cannot see it. */
    @Column(name = "alt_text", length = ALT_MAX)
    private String altText;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    /** A PDF's number of pages. */
    @Column(name = "pages")
    private Integer pages;

    /** Whether a document has a picture of its first page. */
    @Column(name = "has_preview")
    private Boolean hasPreview;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "message_id")
    private Long messageId;

    /** The post held for review it waits with. */
    @Column(name = "held_id")
    private Long heldId;

    /** Its place among the files of its post or message. */
    @Column(name = "position")
    private Integer position;

    public boolean isAttached() {
        return postId != null || messageId != null || heldId != null;
    }

    public boolean isImage() {
        return kind == NetworkFileKind.IMAGE;
    }
}
