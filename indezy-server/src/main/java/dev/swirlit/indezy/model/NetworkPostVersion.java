package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An earlier text of an edited post, kept for its readers to see what changed. */
@Entity
@Table(name = "network_post_versions", indexes = @Index(name = "idx_network_post_versions_post", columnList = "post_id"))
@Getter
@Setter
public class NetworkPostVersion extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "text", nullable = false, length = NetworkPost.TEXT_MAX)
    private String text;
}
