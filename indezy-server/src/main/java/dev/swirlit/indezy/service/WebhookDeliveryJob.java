package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Webhook;
import dev.swirlit.indezy.model.WebhookDelivery;
import dev.swirlit.indezy.model.enums.WebhookDeliveryStatus;
import dev.swirlit.indezy.repository.WebhookDeliveryRepository;
import dev.swirlit.indezy.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sends the webhooks' deliveries (see docs/features.md#webhooks): each a JSON POST signed with its webhook's secret, a 2xx
 * answer within ten seconds counting as received. A failure is tried again after 1, 5 and 30 minutes, 2 and 12 hours, then
 * given up; a webhook failing {@value #DISABLE_AFTER} times in a row is turned off. Redirects are not followed, and the
 * host is resolved again before each attempt so a webhook never reaches a private address.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryJob {

    static final Duration[] RETRIES = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2),
        Duration.ofHours(12)};
    static final int DISABLE_AFTER = 20;
    private static final int BATCH = 20;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration KEEP = Duration.ofDays(30);

    private final WebhookDeliveryRepository deliveries;
    private final WebhookRepository webhooks;
    private final WebhookAddresses addresses;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Value("${indezy.webhooks.delivery-enabled:true}")
    private boolean enabled = true;

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT20S")
    public void run() {
        if (enabled) {
            deliverDue();
        }
    }

    /** Forgets, each night, the deliveries settled more than 30 days ago. */
    @Scheduled(cron = "0 35 4 * * *", zone = "Europe/Paris")
    public void forgetOld() {
        transaction.executeWithoutResult(status -> deliveries.deleteSettledBefore(LocalDateTime.ofInstant(clock.instant().minus(KEEP),
            ZoneOffset.UTC)));
    }

    /** Sends the deliveries due, a batch at a time; answers how many it tried. */
    public int deliverDue() {
        List<Long> claimed;
        try {
            claimed = claim();
        } catch (RuntimeException e) {
            // Another replica claimed the same deliveries first.
            log.debug("Webhook deliveries claimed elsewhere: {}", e.getClass().getSimpleName());
            return 0;
        }
        claimed.forEach(this::deliver);
        return claimed.size();
    }

    /** Leases the due deliveries, so another replica leaves them while they are sent. */
    private List<Long> claim() {
        return transaction.execute(status -> {
            Instant now = clock.instant();
            List<WebhookDelivery> due = deliveries.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                WebhookDeliveryStatus.PENDING, now, PageRequest.of(0, BATCH));
            due.forEach(delivery -> delivery.setNextAttemptAt(now.plus(LEASE)));
            deliveries.flush();
            return due.stream().map(WebhookDelivery::getId).toList();
        });
    }

    private void deliver(Long id) {
        Optional<Attempt> attempt = transaction.execute(status -> deliveries.findById(id).flatMap(delivery ->
            webhooks.findById(delivery.getWebhookId()).filter(Webhook::isActive)
                .map(webhook -> new Attempt(delivery.getId(), delivery.getEvent().name(), delivery.getPayload(), webhook.getUrl(),
                    webhook.getSecret()))));
        Outcome outcome = attempt.map(this::send).orElse(new Outcome(null, "The webhook is off or deleted"));
        transaction.executeWithoutResult(status -> record(id, outcome, attempt.isEmpty()));
    }

    private Outcome send(Attempt attempt) {
        try {
            URI uri = URI.create(attempt.url());
            addresses.requirePublic(uri.getHost());
            String timestamp = String.valueOf(clock.instant().getEpochSecond());
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Indezy-Webhooks/1")
                .header("X-Indezy-Event", attempt.event())
                .header("X-Indezy-Delivery", String.valueOf(attempt.id()))
                .header("X-Indezy-Timestamp", timestamp)
                .header("X-Indezy-Signature", "sha256=" + sign(attempt.secret(), timestamp + "." + attempt.payload()))
                .POST(HttpRequest.BodyPublishers.ofString(attempt.payload(), StandardCharsets.UTF_8))
                .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return new Outcome(status, status / 100 == 2 ? null : "Answered " + status);
        } catch (IllegalArgumentException e) {
            return new Outcome(null, e.getMessage());
        } catch (IOException e) {
            return new Outcome(null, "No answer: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(null, "Interrupted");
        }
    }

    private void record(Long id, Outcome outcome, boolean webhookGone) {
        deliveries.findById(id).ifPresent(delivery -> {
            Instant now = clock.instant();
            delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setLastAttemptAt(now);
            delivery.setResponseStatus(Objects.requireNonNullElse(outcome.status(), 0));
            delivery.setError(outcome.error() == null ? null : outcome.error().substring(0, Math.min(200, outcome.error().length())));
            boolean received = outcome.error() == null;
            if (received) {
                delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
            } else if (webhookGone || delivery.getAttempts() > RETRIES.length) {
                delivery.setStatus(WebhookDeliveryStatus.FAILED);
            } else {
                delivery.setNextAttemptAt(now.plus(RETRIES[delivery.getAttempts() - 1]));
            }
            if (!webhookGone) {
                webhooks.findById(delivery.getWebhookId()).ifPresent(webhook -> {
                    webhook.setLastDeliveryAt(now);
                    webhook.setLastStatus(delivery.getResponseStatus());
                    webhook.setConsecutiveFailures(received ? 0 : webhook.getConsecutiveFailures() + 1);
                    if (webhook.getConsecutiveFailures() >= DISABLE_AFTER) {
                        webhook.setActive(false);
                        log.warn("Turned webhook {} off after {} failures in a row", webhook.getId(), DISABLE_AFTER);
                    }
                });
            }
        });
    }

    /** HMAC-SHA256 of the signed text with the webhook's secret, in hexadecimal. */
    static String sign(String secret, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is part of every Java runtime", e);
        }
    }

    private record Attempt(Long id, String event, String payload, String url, String secret) {
        @Override
        public String toString() {
            return "Attempt[" + id + "]";
        }
    }

    private record Outcome(Integer status, String error) {
    }
}
