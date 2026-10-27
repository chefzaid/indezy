package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.enums.WebhookEvent;

import java.util.Map;

/** Tells the workspace's webhooks of an event, within the transaction of the change it tells of. */
@FunctionalInterface
public interface WebhookPublisher {

    /** Publishes nothing: services built outside Spring, in unit tests. */
    WebhookPublisher NONE = (freelanceId, event, data) -> { };

    void publish(Long freelanceId, WebhookEvent event, Map<String, Object> data);
}
