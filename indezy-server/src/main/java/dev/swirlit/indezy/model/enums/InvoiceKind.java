package dev.swirlit.indezy.model.enums;

/** An invoice, a credit note (avoir) correcting or cancelling an issued one, or a deposit invoice (facture d'acompte) of a quote. */
public enum InvoiceKind {
    INVOICE,
    CREDIT_NOTE,
    /** The deposit an accepted quote asks, which the quote's final invoice then takes off. */
    DEPOSIT
}
