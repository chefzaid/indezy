package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkNotification;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NetworkNotificationRepository extends JpaRepository<NetworkNotification, Long> {

    /** The member's latest notifications, the newest first. */
    List<NetworkNotification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable page);

    long countByRecipientIdAndReadFalse(Long recipientId);

    /** The members with notifications unread and not emailed, from before the given time. */
    @Query("select distinct n.recipientId from NetworkNotification n where n.read = false and (n.emailed is null or n.emailed = false) " +
        "and n.createdAt <= :before")
    List<Long> recipientsToEmail(@Param("before") LocalDateTime before);

    /** A member's notifications unread and not emailed, from before the given time, the newest first. */
    @Query("select n from NetworkNotification n where n.recipientId = :recipientId and n.read = false and (n.emailed is null or " +
        "n.emailed = false) and n.createdAt <= :before order by n.createdAt desc, n.id desc")
    List<NetworkNotification> toEmail(@Param("recipientId") Long recipientId, @Param("before") LocalDateTime before);

    boolean existsByRecipientIdAndActorIdAndTypeAndPostId(Long recipientId, Long actorId, NetworkNotificationType type, Long postId);

    boolean existsByRecipientIdAndActorIdAndTypeAndPostIdIsNull(Long recipientId, Long actorId, NetworkNotificationType type);

    @Modifying
    @Query("update NetworkNotification n set n.read = true where n.recipientId = :recipientId and n.read = false")
    int markRead(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("delete from NetworkNotification n where n.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    /** Deletes the reader's notifications of a kind about a post: the group they dismissed. */
    @Modifying
    @Query("delete from NetworkNotification n where n.recipientId = :recipientId and n.type = :type and n.postId = :postId")
    int dismiss(@Param("recipientId") Long recipientId, @Param("type") NetworkNotificationType type, @Param("postId") Long postId);

    /** The same for a kind about no post (a follow, the moderators' word). */
    @Modifying
    @Query("delete from NetworkNotification n where n.recipientId = :recipientId and n.type = :type and n.postId is null")
    int dismissWithoutPost(@Param("recipientId") Long recipientId, @Param("type") NetworkNotificationType type);
}
