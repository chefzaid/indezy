package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkNotification;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NetworkNotificationRepository extends JpaRepository<NetworkNotification, Long> {

    /** The member's latest notifications, the newest first. */
    List<NetworkNotification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable page);

    long countByRecipientIdAndReadFalse(Long recipientId);

    boolean existsByRecipientIdAndActorIdAndTypeAndPostId(Long recipientId, Long actorId, NetworkNotificationType type, Long postId);

    boolean existsByRecipientIdAndActorIdAndTypeAndPostIdIsNull(Long recipientId, Long actorId, NetworkNotificationType type);

    @Modifying
    @Query("update NetworkNotification n set n.read = true where n.recipientId = :recipientId and n.read = false")
    int markRead(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("delete from NetworkNotification n where n.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
