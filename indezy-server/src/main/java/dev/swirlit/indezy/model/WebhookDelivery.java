package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.WebhookDeliveryStatus;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One event to post to one webhook, written with the change it tells of, then sent and retried by the delivery job. */
@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
public class WebhookDelivery extends BaseEntity {

    @Column(name = "webhook_id", nullable = false)
    private Long webhookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 40)
    private WebhookEvent event;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookDeliveryStatus status = WebhookDeliveryStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    /** The answer's HTTP status, or 0 when none came. */
    @Column(name = "response_status")
    private Integer responseStatus;

    /** Why the last attempt failed, without the answer's body. */
    @Column(name = "error", length = 200)
    private String error;
}
