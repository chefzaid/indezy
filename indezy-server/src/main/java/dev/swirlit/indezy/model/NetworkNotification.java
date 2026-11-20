package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Something a member did that concerns another: a follow, a like, a comment, a reply, a mention or a repost. */
@Entity
@Table(name = "network_notifications", indexes = @Index(name = "idx_network_notifications_recipient", columnList = "recipient_id"))
@Getter
@Setter
public class NetworkNotification extends BaseEntity {

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NetworkNotificationType type;

    /** The post it is about, if any. */
    @Column(name = "post_id")
    private Long postId;

    /** The community it is about, for a community's activity; none otherwise. */
    @Column(name = "community_id")
    private Long communityId;

    /** The idea of the ideas board it is about, if any. */
    @Column(name = "idea_id")
    private Long ideaId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    /** Emailed to its recipient; none (null) reads as not emailed. */
    @Column(name = "emailed")
    private Boolean emailed;
}
