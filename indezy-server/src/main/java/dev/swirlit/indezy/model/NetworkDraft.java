package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A post its author keeps to publish later: by hand, or at the time they set. */
@Entity
@Table(name = "network_drafts", indexes = @Index(name = "idx_network_drafts_author", columnList = "author_id"))
@Getter
@Setter
public class NetworkDraft extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "text", nullable = false, length = NetworkPost.TEXT_MAX)
    private String text;

    /** When it is published on its own; none for a draft kept until its author publishes it. */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
}
