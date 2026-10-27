package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.enums.DocumentLanguage;

/** French and English text of the invoice PDF, mandatory mentions included. */
enum InvoiceLabel {
    TITLE("Facture", "Invoice"),
    DRAFT("BROUILLON — ce document n'est pas une facture", "DRAFT — this document is not an invoice"),
    NUMBER("N° %s", "No. %s"),
    ISSUED("Date d'émission : %s", "Issue date: %s"),
    DUE("Échéance : %s", "Due date: %s"),
    PERIOD("Période : du %s au %s", "Period: %s to %s"),
    BILL_TO("Facturé à", "Bill to"),
    SIRET("SIRET : %s", "SIRET: %s"),
    SIREN("SIREN : %s", "SIREN: %s"),
    VAT_NUMBER("N° TVA intracommunautaire : %s", "VAT number: %s"),
    CAPITAL("%s au capital de %s", "%s with a share capital of %s"),
    COLUMN_DESCRIPTION("Désignation", "Description"),
    COLUMN_QUANTITY("Qté", "Qty"),
    COLUMN_PRICE("P.U. HT", "Unit price"),
    COLUMN_VAT("TVA", "VAT"),
    COLUMN_AMOUNT("Montant HT", "Amount"),
    TOTAL_EXCLUDING("Total HT", "Total excl. VAT"),
    TOTAL_VAT("TVA", "VAT"),
    TOTAL("Total TTC", "Total incl. VAT"),
    TOTAL_EXEMPT("Net à payer", "Amount due"),
    VAT_EXEMPT("TVA non applicable, art. 293 B du CGI", "VAT not applicable, art. 293 B of the French General Tax Code"),
    PAYMENT("Paiement à %d jours, au plus tard le %s, par virement.", "Payment within %d days, by %s, by bank transfer."),
    BANK("IBAN : %s", "IBAN: %s"),
    BIC("BIC : %s", "BIC: %s"),
    DISCOUNT("Pas d'escompte pour paiement anticipé.", "No discount for early payment."),
    PENALTIES("En cas de retard de paiement, des pénalités sont dues au taux d'intérêt appliqué par la Banque centrale européenne " +
        "à son opération de refinancement la plus récente, majoré de 10 points, ainsi qu'une indemnité forfaitaire pour frais " +
        "de recouvrement de 40 € (art. L441-10 du Code de commerce).",
        "Late payment incurs penalties at the European Central Bank's most recent refinancing rate plus 10 percentage points, " +
            "and a fixed recovery fee of €40 (art. L441-10 of the French Commercial Code)."),
    NOTES("Notes", "Notes"),
    INDIVIDUAL(" EI", " EI");

    private final String french;
    private final String english;

    InvoiceLabel(String french, String english) {
        this.french = french;
        this.english = english;
    }

    String in(DocumentLanguage language) {
        return language == DocumentLanguage.EN ? english : french;
    }

    String format(DocumentLanguage language, Object... values) {
        return String.format(ActivityReportLabel.locale(language), in(language), values);
    }
}
