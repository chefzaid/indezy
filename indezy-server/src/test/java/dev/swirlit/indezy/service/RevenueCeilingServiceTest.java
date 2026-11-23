package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RevenueCeilingServiceTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final RevenueCeilingService service = new RevenueCeilingService(invoices, freelances,
        Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC));
    private final List<Invoice> all = new ArrayList<>();
    private long ids = 1;

    private void workspace(LegalForm form, Boolean exempt) {
        Freelance freelance = new Freelance();
        BillingIdentity billing = new BillingIdentity();
        billing.setLegalForm(form);
        billing.setVatExempt(exempt);
        freelance.setBilling(billing);
        when(freelances.findById(7L)).thenReturn(Optional.of(freelance));
        when(invoices.findByFreelanceIdOrderByCreatedAtDesc(7L)).thenReturn(all);
    }

    private Invoice invoice(String amount, InvoiceStatus status, LocalDate paidOn) {
        Invoice invoice = new Invoice();
        invoice.setId(ids++);
        invoice.setNumber("F-" + invoice.getId());
        invoice.setStatus(status);
        invoice.setIssueDate(paidOn == null ? LocalDate.of(2026, 11, 1) : paidOn.minusDays(30));
        invoice.setPaidOn(paidOn);
        InvoiceLine line = new InvoiceLine();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(amount));
        line.setVatRate(BigDecimal.ZERO);
        invoice.setLines(List.of(line));
        all.add(invoice);
        return invoice;
    }

    @Test
    void measuresTheRevenueReceivedAgainstTheCeilingsAndSaysWhenTheFranchiseEnds() {
        workspace(LegalForm.MICRO_ENTREPRISE, null);
        invoice("20000", InvoiceStatus.PAID, LocalDate.of(2026, 3, 10));
        Invoice credited = invoice("15000", InvoiceStatus.PAID, LocalDate.of(2026, 6, 15));
        Invoice note = invoice("1000", InvoiceStatus.ISSUED, null);
        note.setKind(InvoiceKind.CREDIT_NOTE);
        note.setCreditedInvoiceId(credited.getId());
        note.setIssueDate(LocalDate.of(2026, 6, 1));
        invoice("8000", InvoiceStatus.PAID, LocalDate.of(2026, 9, 30));
        invoice("5000", InvoiceStatus.ISSUED, null);
        invoice("30000", InvoiceStatus.PAID, LocalDate.of(2025, 12, 20));

        RevenueCeilingService.Ceilings ceilings = service.ceilings(7L, null);

        assertThat(ceilings.year()).isEqualTo(2026);
        assertThat(ceilings.received()).isEqualByComparingTo("42000");
        assertThat(ceilings.receivedLastYear()).isEqualByComparingTo("30000");
        assertThat(ceilings.outstanding()).isEqualByComparingTo("5000");
        assertThat(ceilings.micro().limit()).isEqualByComparingTo("83600");
        assertThat(ceilings.micro().percent()).isEqualTo(50);
        assertThat(ceilings.micro().level()).isEqualTo(RevenueCeilingService.Level.UNDER);
        // 34 000 by June, 42 000 on 30 September: past the 41 250 threshold that day.
        assertThat(ceilings.vat().level()).isEqualTo(RevenueCeilingService.Level.ENDS);
        assertThat(ceilings.vat().endsOn()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void endsTheFranchiseOnTheFirstOfJanuaryAfterTheBaseThresholdAndTheRegimeAfterTwoYearsOver() {
        assertThat(RevenueCeilingService.micro(2026, new BigDecimal("90000"), new BigDecimal("80000")))
            .extracting(RevenueCeilingService.Ceiling::level, RevenueCeilingService.Ceiling::endsOn)
            .containsExactly(RevenueCeilingService.Level.ENDS, LocalDate.of(2027, 1, 1));
        assertThat(RevenueCeilingService.micro(2026, new BigDecimal("90000"), new BigDecimal("70000")).level())
            .isEqualTo(RevenueCeilingService.Level.OVER);
        assertThat(RevenueCeilingService.micro(2026, new BigDecimal("70000"), BigDecimal.ZERO).level())
            .isEqualTo(RevenueCeilingService.Level.NEAR);

        workspace(LegalForm.EI, true);
        invoice("39000", InvoiceStatus.PAID, LocalDate.of(2026, 5, 4));
        RevenueCeilingService.Ceilings ceilings = service.ceilings(7L, 2026);
        assertThat(ceilings.micro()).isNull();
        assertThat(ceilings.vat().endsOn()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(service.ceilings(7L, 2027).vat().endsOn()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void leavesOutTheFranchiseWhenChargingVat() {
        workspace(LegalForm.SASU, false);
        assertThat(service.ceilings(7L, 2026)).extracting(RevenueCeilingService.Ceilings::micro, RevenueCeilingService.Ceilings::vat)
            .containsExactly(null, null);
    }
}
