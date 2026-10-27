package dev.swirlit.indezy.model.enums;

/** Where an invoice stands; an issued invoice past its due date reads as overdue. */
public enum InvoiceStatus {
    /** Being written: no number, can change or go. */
    DRAFT,
    /** Numbered and sent: it can no longer change. */
    ISSUED,
    PAID
}
