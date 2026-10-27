package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountingExportServiceTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final AccountingExportService service = new AccountingExportService(invoices, freelances);

    @Test
    void paysInTheYearAnInvoiceOfTheYearBeforeLessOnlyTheCreditNotesIssuedByThen() {
        Invoice december = invoice(1L, "F2025-0040", LocalDate.of(2025, 12, 20), "1000");
        december.setStatus(InvoiceStatus.PAID);
        december.setPaidOn(LocalDate.of(2026, 1, 15));
        Invoice before = creditNote(2L, "F2026-0001", LocalDate.of(2026, 1, 10), december, "200");
        Invoice after = creditNote(3L, "F2026-0002", LocalDate.of(2026, 2, 1), december, "100");
        // Credited in full before its payment: no money came in.
        Invoice cancelled = invoice(4L, "F2026-0003", LocalDate.of(2026, 1, 12), "300");
        cancelled.setStatus(InvoiceStatus.CREDITED);
        cancelled.setPaidOn(LocalDate.of(2026, 1, 20));
        Invoice cancelling = creditNote(5L, "F2026-0004", LocalDate.of(2026, 1, 13), cancelled, "300");
        when(invoices.findByFreelanceIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(december, before, after, cancelled, cancelling));
        when(freelances.findById(7L)).thenReturn(Optional.empty());

        AccountingExportService.ExportFile fec = service.export(7L, 2026, AccountingExportService.Format.FEC, DocumentLanguage.FR);

        assertThat(fec.fileName()).isEqualTo("FEC20261231.txt");
        assertThat(fec.contentType()).startsWith("text/plain");
        List<String> lines = new String(fec.content(), StandardCharsets.UTF_8).lines().toList();
        assertThat(lines).filteredOn(line -> line.startsWith("BQ")).hasSize(2).allSatisfy(line -> assertThat(line)
            .contains("\tF2025-0040\t20260115\tRèglement F2025-0040\t", "800,00"));
        // Exempt from VAT: no VAT line; the invoice of the year before stays out of the sales journal.
        assertThat(lines).noneMatch(line -> line.contains("445710") || line.contains("Facture F2025-0040"));
        assertThat(lines).filteredOn(line -> line.startsWith("VE")).hasSize(8);
    }

    @Test
    void namesTheFileAfterTheSirenOnlyWhenTheSiretIsKnown() {
        when(invoices.findByFreelanceIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        Freelance freelance = new Freelance();
        BillingIdentity billing = new BillingIdentity();
        billing.setSiret("1234");
        freelance.setBilling(billing);
        when(freelances.findById(7L)).thenReturn(Optional.of(freelance));
        assertThat(service.export(7L, 2026, AccountingExportService.Format.FEC, DocumentLanguage.FR).fileName())
            .isEqualTo("FEC20261231.txt");
        freelance.setBilling(null);
        assertThat(service.export(7L, 2026, AccountingExportService.Format.FEC, DocumentLanguage.FR).fileName())
            .isEqualTo("FEC20261231.txt");
    }

    @Test
    void listsEachStatusInTheRegisterWithoutAKnownBuyer() {
        Invoice paid = invoice(1L, "F2026-0001", LocalDate.of(2026, 3, 1), "100");
        paid.setStatus(InvoiceStatus.PAID);
        Invoice credited = invoice(2L, "F2026-0002", LocalDate.of(2026, 3, 2), "50");
        credited.setStatus(InvoiceStatus.CREDITED);
        Invoice waiting = invoice(3L, "F2026-0003", LocalDate.of(2026, 3, 3), "25");
        waiting.setBuyer(null);
        Invoice note = creditNote(4L, "F2026-0004", LocalDate.of(2026, 3, 4), credited, "50");
        Invoice draft = invoice(5L, null, null, "999");
        when(invoices.findByFreelanceIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(paid, credited, waiting, note, draft));

        String english = new String(service.export(7L, 2026, AccountingExportService.Format.REGISTER, DocumentLanguage.EN).content(),
            StandardCharsets.UTF_8);
        assertThat(english.lines()).hasSize(6);
        assertThat(english).contains(",Paid,", ",Credited,", ",,Awaiting payment,", "Credit note", "Total,,,,,,,125.00,0.00,125.00");
        String french = new String(service.export(7L, 2026, AccountingExportService.Format.REGISTER, DocumentLanguage.FR).content(),
            StandardCharsets.UTF_8);
        assertThat(french).contains(";Payée;", ";Annulée par avoir;", ";En attente de paiement;", ";Avoir;", "Total;;;;;;;125,00");
    }

    private static Invoice invoice(Long id, String number, LocalDate issueDate, String amount) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setClientId(3L);
        invoice.setNumber(number);
        invoice.setIssueDate(issueDate);
        invoice.setStatus(number == null ? InvoiceStatus.DRAFT : InvoiceStatus.ISSUED);
        invoice.setBuyer(new InvoiceBuyer());
        invoice.setLines(List.of(new InvoiceLine("Audit", BigDecimal.ONE, new BigDecimal(amount), BigDecimal.ZERO)));
        return invoice;
    }

    private static Invoice creditNote(Long id, String number, LocalDate issueDate, Invoice credited, String amount) {
        Invoice note = invoice(id, number, issueDate, amount);
        note.setKind(InvoiceKind.CREDIT_NOTE);
        note.setCreditedInvoiceId(credited.getId());
        note.setCreditedNumber(credited.getNumber());
        return note;
    }
}
