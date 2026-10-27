package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.LegalForm;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    private static InvoiceSeller company() {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Martin Conseil");
        seller.setLegalForm(LegalForm.SASU);
        seller.setShareCapital(1000);
        seller.setRegistration("RCS Paris 552 100 554");
        seller.setSiret("55210055400012");
        seller.setVatNumber("FR40552100554");
        seller.setVatExempt(false);
        seller.setAddress("18 rue du Sentier\n75002 Paris");
        seller.setIban("FR7630006000011234567890189");
        seller.setBic("BNPAFRPP");
        return seller;
    }

    private static InvoiceBuyer buyer() {
        InvoiceBuyer buyer = new InvoiceBuyer();
        buyer.setName("Société Générale");
        buyer.setAddress("29 boulevard Haussmann\nParis");
        buyer.setVatNumber("FR27552120222");
        return buyer;
    }

    private static Invoice invoice(InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setStatus(status);
        invoice.setLanguage(DocumentLanguage.FR);
        if (status != InvoiceStatus.DRAFT) {
            invoice.setNumber("F2026-0007");
            invoice.setIssueDate(LocalDate.of(2026, 12, 1));
            invoice.setDueDate(LocalDate.of(2026, 12, 31));
        }
        invoice.setPeriodStart(LocalDate.of(2026, 11, 1));
        invoice.setPeriodEnd(LocalDate.of(2026, 11, 30));
        invoice.setNotes("Bon de commande 4500012345");
        invoice.setLines(List.of(new InvoiceLine("Prestation de développement Java pour la refonte du système de facturation, " +
            "y compris les revues de code et l'accompagnement de l'équipe", new BigDecimal("18.50"), new BigDecimal("650.00"),
            new BigDecimal("20.00"))));
        return invoice;
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getDocumentInformation().getCreator()).isEqualTo("Indezy");
            return new PDFTextStripper().getText(document).replaceAll("[ \\t]+", " ");
        }
    }

    @Test
    void statesACompanysCapitalRegistrationAndVatInFrench() throws IOException {
        String text = text(renderer.render(invoice(InvoiceStatus.ISSUED), company(), buyer()));

        assertThat(text).contains("Facture", "N° F2026-0007", "Martin Conseil", "SASU au capital de 1 000,00 €", "RCS Paris 552 100 554",
            "SIRET : 55210055400012", "N° TVA intracommunautaire : FR40552100554", "Facturé à", "Société Générale",
            "N° TVA intracommunautaire : FR27552120222", "Date d'émission : 1 décembre 2026", "Échéance : 31 décembre 2026",
            "Période : du 1 novembre 2026 au 30 novembre 2026", "18,5", "650,00 €", "20 %", "12 025,00 €", "Total HT",
            "2 405,00 €", "Total TTC", "14 430,00 €", "Paiement à 30 jours, au plus tard le 31 décembre 2026",
            "IBAN : FR76 3000 6000 0112 3456 7890 189", "BIC : BNPAFRPP", "Pas d'escompte", "40 €", "Bon de commande 4500012345",
            "y compris les revues de code")
            .doesNotContain("293 B", "Net à payer", "BROUILLON", "Martin Conseil EI");
    }

    @Test
    void marksADraftAndGivesItNeitherNumberNorDates() throws IOException {
        InvoiceSeller seller = company();
        seller.setLegalForm(LegalForm.EI);
        seller.setVatExempt(true);
        String text = text(renderer.render(invoice(InvoiceStatus.DRAFT), seller, new InvoiceBuyer()));

        assertThat(text).contains("BROUILLON — ce document n'est pas une facture", "Martin Conseil EI", "TVA non applicable, art. 293 B du CGI",
            "Net à payer").doesNotContain("N° F", "Date d'émission", "Total TTC", "FR40552100554");
    }
}
