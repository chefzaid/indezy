package dev.swirlit.indezy.model.enums;

/**
 * What a webhook can be told: an opportunity added or moved, an activity report sent or answered, an invoice issued or
 * paid, a call booked through the booking link.
 */
public enum WebhookEvent {
    OPPORTUNITY_CREATED,
    OPPORTUNITY_STATUS_CHANGED,
    ACTIVITY_REPORT_SUBMITTED,
    ACTIVITY_REPORT_APPROVED,
    ACTIVITY_REPORT_REJECTED,
    INVOICE_ISSUED,
    INVOICE_PAID,
    CALL_BOOKED,
    /** The test sent on demand, to every webhook whatever it listens to. */
    PING
}
