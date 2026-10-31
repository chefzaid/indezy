package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoicePaymentDelaysTest {

    private static Invoice paid(long clientId, String issued, String due, String paidOn) {
        Invoice invoice = new Invoice();
        invoice.setClientId(clientId);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setIssueDate(LocalDate.parse(issued));
        invoice.setDueDate(due == null ? null : LocalDate.parse(due));
        invoice.setPaidOn(paidOn == null ? null : LocalDate.parse(paidOn));
        return invoice;
    }

    @Test
    void averagesEachClientsDaysToPaymentAndPastTheDueDate() {
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        Invoice note = paid(1, "2026-03-01", null, "2026-03-02");
        note.setKind(InvoiceKind.CREDIT_NOTE);
        when(invoices.findByFreelanceIdAndStatus(7L, InvoiceStatus.PAID)).thenReturn(List.of(
            // Client 2: 40 days, 10 late; 20 days, early.
            paid(2, "2026-01-01", "2026-01-31", "2026-02-10"),
            paid(2, "2026-02-01", "2026-03-03", "2026-02-21"),
            // Client 1: 61 days, 31 late; no due date counts no lateness.
            paid(1, "2026-04-01", "2026-05-01", "2026-06-01"),
            paid(1, "2026-04-01", null, "2026-04-11"),
            note,
            paid(3, "2026-04-01", "2026-05-01", null)));

        assertThat(new InvoicePaymentDelays(invoices).byClient(7L)).containsExactly(
            new InvoiceDtos.PaymentDelay(1L, 2, 36, 16),
            new InvoiceDtos.PaymentDelay(2L, 2, 30, 5));
    }
}
