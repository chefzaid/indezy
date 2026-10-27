package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.WebhookDelivery;
import dev.swirlit.indezy.model.enums.WebhookDeliveryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    /** The deliveries due, the oldest first. */
    List<WebhookDelivery> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(WebhookDeliveryStatus status, Instant now,
                                                                                         Pageable page);

    /** A webhook's latest deliveries, the newest first. */
    List<WebhookDelivery> findByWebhookIdOrderByIdDesc(Long webhookId, Pageable page);

    @Modifying
    @Query("DELETE FROM WebhookDelivery d WHERE d.webhookId = :webhookId")
    void deleteByWebhookId(@Param("webhookId") Long webhookId);

    /** Forgets the deliveries made before the given time; the pending ones stay. */
    @Modifying
    @Query("DELETE FROM WebhookDelivery d WHERE d.createdAt < :before AND d.status <> dev.swirlit.indezy.model.enums.WebhookDeliveryStatus.PENDING")
    int deleteSettledBefore(@Param("before") LocalDateTime before);
}
