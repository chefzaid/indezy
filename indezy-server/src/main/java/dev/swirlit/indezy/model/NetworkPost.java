package dev.swirlit.indezy.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/** A member's post to the network: its text and the #tags it names, kept lower-case. */
@Entity
@Table(name = "network_posts", indexes = @Index(name = "idx_network_posts_author", columnList = "author_id"))
@Getter
@Setter
public class NetworkPost extends BaseEntity {

    public static final int TEXT_MAX = 3000;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "text", nullable = false, length = TEXT_MAX)
    private String text;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "network_post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag", length = 40)
    private Set<String> tags = new LinkedHashSet<>();

    public Set<String> getTags() {
        return tags != null ? new LinkedHashSet<>(tags) : new LinkedHashSet<>();
    }

    public void setTags(final Set<String> tags) {
        this.tags = tags != null ? new LinkedHashSet<>(tags) : new LinkedHashSet<>();
    }
}
