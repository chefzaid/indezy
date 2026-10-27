package dev.swirlit.indezy.model.enums;

/** A delivery waits for its next attempt, was received (a 2xx answer), or failed every attempt. */
public enum WebhookDeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED
}
