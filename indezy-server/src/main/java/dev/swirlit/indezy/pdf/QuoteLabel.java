package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.enums.DocumentLanguage;

/** French and English text of the quote PDF; what it shares with the invoice is in {@link InvoiceLabel}. */
enum QuoteLabel {
    TITLE("Devis", "Quote"),
    DRAFT("BROUILLON — ce document n'est pas un devis envoyé", "DRAFT — this quote has not been sent"),
    ISSUED("Date : %s", "Date: %s"),
    VALID_UNTIL("Valable jusqu'au %s", "Valid until %s"),
    FOR("Client", "Client"),
    VALIDITY("Offre valable %d jours à compter de sa date, soit jusqu'au %s.", "This offer holds %d days from its date, until %s."),
    VALIDITY_DRAFT("Offre valable %d jours à compter de sa date d'envoi.", "This offer holds %d days from the day it is sent."),
    DEPOSIT("Acompte de %d %% à la commande, soit %s, facturé à l'acceptation du devis ; le solde à la livraison.",
        "A %d%% deposit on order, that is %s, invoiced on acceptance; the balance on delivery."),
    PAYMENT("Paiement à %d jours à réception de la facture, par virement.", "Payment within %d days of the invoice, by bank transfer."),
    FREE("Devis gratuit.", "This quote is free of charge."),
    ACCEPTANCE("Bon pour accord — date, signature et cachet du client, précédés de la mention « Bon pour accord » :",
        "Accepted — date, signature and stamp of the client, preceded by the words \"Accepted\":");

    private final String french;
    private final String english;

    QuoteLabel(String french, String english) {
        this.french = french;
        this.english = english;
    }

    String in(DocumentLanguage language) {
        return language == DocumentLanguage.EN ? english : french;
    }

    String format(DocumentLanguage language, Object... values) {
        return String.format(language.locale(), in(language), values);
    }
}
