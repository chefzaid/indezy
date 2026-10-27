package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.WebhookDeliveryStatus;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** The workspace's webhooks and their deliveries, as shown, and the requests that write them. */
public final class WebhookDtos {

    private WebhookDtos() {
    }

    /** An HTTPS address and the events it is told of. */
    public record Write(@NotBlank @Size(max = 500) String url, @NotEmpty Set<@NotNull WebhookEvent> events, Boolean active) {
        public Write {
            events = events == null ? null : Set.copyOf(events);
        }
    }

    public record Webhook(Long id, String url, List<WebhookEvent> events, boolean active, int consecutiveFailures, Instant lastDeliveryAt,
                          Integer lastStatus, LocalDateTime createdAt) {
        public Webhook {
            events = List.copyOf(events);
        }
    }

    /** A new webhook and the secret of its signatures, shown this once. */
    public record Created(Webhook webhook, String secret) {
        @Override
        public String toString() {
            return "Created[webhook=" + (webhook == null ? null : webhook.id()) + "]";
        }
    }

    public record Delivery(Long id, WebhookEvent event, WebhookDeliveryStatus status, int attempts, Instant nextAttemptAt,
                           Instant lastAttemptAt, Integer responseStatus, String error, LocalDateTime createdAt) {
    }
}
