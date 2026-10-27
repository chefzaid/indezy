package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.WebhookEvent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * An address of the freelancer's own tools that the workspace's events are posted to, signed with the webhook's secret.
 * It turns itself off after too many failures in a row.
 */
@Entity
@Table(name = "webhooks")
@Getter
@Setter
public class Webhook extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** The HMAC key of the signatures, given to the freelancer when the webhook is created. */
    @Column(name = "secret", nullable = false, length = 64)
    private String secret;

    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "webhook_events", joinColumns = @JoinColumn(name = "webhook_id"))
    @Column(name = "event", nullable = false, length = 40)
    private Set<WebhookEvent> events = EnumSet.noneOf(WebhookEvent.class);

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_delivery_at")
    private Instant lastDeliveryAt;

    /** The last answer's HTTP status, or 0 when none came. */
    @Column(name = "last_status")
    private Integer lastStatus;

    public Set<WebhookEvent> getEvents() {
        return events.isEmpty() ? Set.of() : EnumSet.copyOf(events);
    }

    public void setEvents(Set<WebhookEvent> events) {
        this.events = events.isEmpty() ? EnumSet.noneOf(WebhookEvent.class) : EnumSet.copyOf(events);
    }

    @Override
    public String toString() {
        return "Webhook[" + getId() + "]";
    }
}
