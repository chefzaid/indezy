package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A community of the member network (Kafka, Lyon...): joined at will, with its own feed, moderated by its creator and co-moderators. */
@Entity
@Table(name = "network_communities")
@Getter
@Setter
public class NetworkCommunity extends BaseEntity {

    public static final int NAME_MAX = 60;
    public static final int DESCRIPTION_MAX = 500;

    /** Its address, `/network/communities/<slug>`: made from its name, unique. */
    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "description", length = DESCRIPTION_MAX)
    private String description;

    /** Joined on request, its moderators answering; its posts read by its members alone. None reads as open. */
    @Column(name = "private_community")
    private Boolean privateCommunity;

    /** The member who created it, its first moderator. */
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    public boolean isPrivate() {
        return Boolean.TRUE.equals(privateCommunity);
    }
}
