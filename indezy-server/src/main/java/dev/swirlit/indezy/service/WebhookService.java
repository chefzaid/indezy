package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.WebhookDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Webhook;
import dev.swirlit.indezy.model.WebhookDelivery;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import dev.swirlit.indezy.repository.WebhookDeliveryRepository;
import dev.swirlit.indezy.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The workspace's webhooks (see docs/features.md#webhooks): up to {@value #MAX_WEBHOOKS} HTTPS addresses, each told of the
 * events it chose. An event becomes one delivery per webhook in the transaction of its change, so nothing is told of a
 * change that did not happen; {@link WebhookDeliveryJob} sends them.
 */
@Service
@RequiredArgsConstructor
public class WebhookService implements WebhookPublisher {

    static final int MAX_WEBHOOKS = 5;
    private static final int SECRET_BYTES = 32;
    private static final int LISTED_DELIVERIES = 20;

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookAddresses addresses;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public List<WebhookDtos.Webhook> list(Long freelanceId) {
        return webhooks.findByFreelanceIdOrderByIdAsc(freelanceId).stream().map(WebhookService::toDto).toList();
    }

    @Transactional
    public WebhookDtos.Created create(Long freelanceId, WebhookDtos.Write write) {
        if (webhooks.countByFreelanceId(freelanceId) >= MAX_WEBHOOKS) {
            throw new InvalidStateException("A workspace has " + MAX_WEBHOOKS + " webhooks at most: delete one first");
        }
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        Webhook webhook = new Webhook();
        webhook.setFreelanceId(freelanceId);
        webhook.setSecret(HexFormat.of().formatHex(bytes));
        apply(webhook, write);
        Webhook saved = webhooks.save(webhook);
        return new WebhookDtos.Created(toDto(saved), saved.getSecret());
    }

    /** Changes the address or the events, or turns the webhook back on (its failures forgotten) or off. */
    @Transactional
    public WebhookDtos.Webhook update(Long freelanceId, Long id, WebhookDtos.Write write) {
        Webhook webhook = webhook(freelanceId, id);
        apply(webhook, write);
        if (write.active() != null) {
            if (write.active() && !webhook.isActive()) {
                webhook.setConsecutiveFailures(0);
            }
            webhook.setActive(write.active());
        }
        return toDto(webhook);
    }

    @Transactional
    public void delete(Long freelanceId, Long id) {
        Webhook webhook = webhook(freelanceId, id);
        deliveries.deleteByWebhookId(webhook.getId());
        webhooks.delete(webhook);
    }

    /** Sends a test to the webhook, whatever events it listens to. */
    @Transactional
    public WebhookDtos.Delivery ping(Long freelanceId, Long id) {
        Webhook webhook = webhook(freelanceId, id);
        return toDto(enqueue(webhook, WebhookEvent.PING, Map.of("webhookId", webhook.getId())));
    }

    @Transactional(readOnly = true)
    public List<WebhookDtos.Delivery> deliveries(Long freelanceId, Long id) {
        Webhook webhook = webhook(freelanceId, id);
        return deliveries.findByWebhookIdOrderByIdDesc(webhook.getId(), PageRequest.of(0, LISTED_DELIVERIES)).stream()
            .map(WebhookService::toDto).toList();
    }

    @Override
    @Transactional
    public void publish(Long freelanceId, WebhookEvent event, Map<String, Object> data) {
        if (freelanceId == null) {
            return;
        }
        for (Webhook webhook : webhooks.findByFreelanceIdAndActiveTrue(freelanceId)) {
            if (webhook.getEvents().contains(event)) {
                enqueue(webhook, event, data);
            }
        }
    }

    private WebhookDelivery enqueue(Webhook webhook, WebhookEvent event, Map<String, Object> data) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhookId(webhook.getId());
        delivery.setEvent(event);
        delivery.setNextAttemptAt(clock.instant());
        delivery.setPayload("{}");
        WebhookDelivery saved = deliveries.save(delivery);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", saved.getId());
        body.put("event", event);
        body.put("createdAt", clock.instant().toString());
        body.put("data", data);
        try {
            saved.setPayload(jsonMapper.writeValueAsString(body));
        } catch (JacksonException e) {
            throw new IllegalStateException("A webhook payload could not be written", e);
        }
        return saved;
    }

    private void apply(Webhook webhook, WebhookDtos.Write write) {
        webhook.setUrl(addresses.requireValid(write.url().strip()).toString());
        Set<WebhookEvent> events = EnumSet.copyOf(write.events());
        events.remove(WebhookEvent.PING);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one event");
        }
        webhook.setEvents(events);
    }

    private Webhook webhook(Long freelanceId, Long id) {
        return webhooks.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Webhook not found"));
    }

    private static WebhookDtos.Webhook toDto(Webhook webhook) {
        return new WebhookDtos.Webhook(webhook.getId(), webhook.getUrl(), webhook.getEvents().stream().sorted().toList(), webhook.isActive(),
            webhook.getConsecutiveFailures(), webhook.getLastDeliveryAt(), webhook.getLastStatus(), webhook.getCreatedAt());
    }

    private static WebhookDtos.Delivery toDto(WebhookDelivery delivery) {
        return new WebhookDtos.Delivery(delivery.getId(), delivery.getEvent(), delivery.getStatus(), delivery.getAttempts(),
            delivery.getNextAttemptAt(), delivery.getLastAttemptAt(), delivery.getResponseStatus(), delivery.getError(), delivery.getCreatedAt());
    }
}
