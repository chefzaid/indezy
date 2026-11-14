package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.QuoteStatus;
import dev.swirlit.indezy.pdf.InvoicePdfRenderer;
import dev.swirlit.indezy.pdf.QuotePdfRenderer;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InvoiceCounterRepository;
import dev.swirlit.indezy.repository.InvoiceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.QuoteCounterRepository;
import dev.swirlit.indezy.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How a quote is billed: its deposit per VAT rate, then its balance less the deposits issued. */
class QuoteBillingTest {

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);
    private final InvoiceService invoices = new InvoiceService(invoiceRepository, mock(InvoiceCounterRepository.class),
        mock(ClientRepository.class), mock(ProjectRepository.class), mock(FreelanceRepository.class), mock(ActivityReportService.class),
        mock(InvoicePdfRenderer.class), clock);
    private final QuoteService quoteService = new QuoteService(quotes, mock(QuoteCounterRepository.class), mock(ClientRepository.class),
        mock(ProjectRepository.class), mock(FreelanceRepository.class), invoices, invoiceRepository, mock(QuotePdfRenderer.class), clock);
    private Quote quote;

    @BeforeEach
    void setUp() {
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        quote = new Quote();
        quote.setId(5L);
        quote.setFreelanceId(1L);
        quote.setClientId(3L);
        quote.setNumber("D2026-0001");
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setLanguage(DocumentLanguage.EN);
        quote.setDepositPercent(30);
        quote.setLines(List.of(line("Audit", "2", "800.00", "20.00"), line("Workshop", "1", "333.33", "20.00"),
            line("Book", "1", "50.00", "5.50")));
        when(quotes.findByIdAndFreelanceId(5L, 1L)).thenReturn(Optional.of(quote));
        when(invoiceRepository.findByQuoteId(5L)).thenReturn(List.of());
    }

    private static InvoiceLine line(String description, String quantity, String price, String rate) {
        return new InvoiceLine(description, new BigDecimal(quantity).setScale(2), new BigDecimal(price), new BigDecimal(rate));
    }

    private static Invoice deposit(InvoiceStatus status, String number) {
        Invoice deposit = new Invoice();
        deposit.setKind(InvoiceKind.DEPOSIT);
        deposit.setStatus(status);
        deposit.setNumber(number);
        deposit.setIssueDate(LocalDate.of(2026, 11, 2));
        deposit.setLines(List.of(line("Deposit", "1", "579.99", "20.00"), line("Deposit", "1", "15.00", "5.50")));
        return deposit;
    }

    @Test
    void asksTheDepositOncePerVatRateOfTheQuote() {
        InvoiceDtos.Invoice drafted = quoteService.deposit(1L, 5L);

        assertThat(drafted.kind()).isEqualTo(InvoiceKind.DEPOSIT);
        assertThat(drafted.lines()).extracting(InvoiceDtos.Line::description, InvoiceDtos.Line::unitPrice, InvoiceDtos.Line::vatRate)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("30% deposit on quote No. D2026-0001 — VAT 5.5 %", new BigDecimal("15.00"), new BigDecimal("5.50")),
                org.assertj.core.groups.Tuple.tuple("30% deposit on quote No. D2026-0001 — VAT 20 %", new BigDecimal("580.00"), new BigDecimal("20.00")));
    }

    @Test
    void refusesADepositNotAskedOrAlreadyInvoicedButAsksAgainOnceCredited() {
        when(invoiceRepository.findByQuoteId(5L)).thenReturn(List.of(deposit(InvoiceStatus.ISSUED, "F2026-0004")));
        assertThatThrownBy(() -> quoteService.deposit(1L, 5L)).isInstanceOf(InvalidStateException.class)
            .hasMessageContaining("already invoiced");
        when(invoiceRepository.findByQuoteId(5L)).thenReturn(List.of(deposit(InvoiceStatus.CREDITED, "F2026-0004")));
        assertThat(quoteService.deposit(1L, 5L).kind()).isEqualTo(InvoiceKind.DEPOSIT);
        quote.setDepositPercent(null);
        assertThatThrownBy(() -> quoteService.deposit(1L, 5L)).hasMessage("The quote asks no deposit");
        quote.setStatus(QuoteStatus.SENT);
        assertThatThrownBy(() -> quoteService.invoice(1L, 5L)).hasMessage("Only an accepted quote can be invoiced");
    }

    @Test
    void takesTheIssuedDepositsOffTheBalanceLineForLine() {
        when(invoiceRepository.findByQuoteId(5L)).thenReturn(List.of(deposit(InvoiceStatus.DRAFT, null)));
        assertThatThrownBy(() -> quoteService.invoice(1L, 5L)).hasMessage("Issue the deposit invoice before invoicing the balance");

        when(invoiceRepository.findByQuoteId(5L)).thenReturn(List.of(deposit(InvoiceStatus.ISSUED, "F2026-0004"),
            deposit(InvoiceStatus.CREDITED, "F2026-0003")));
        InvoiceDtos.Invoice balance = quoteService.invoice(1L, 5L);

        assertThat(balance.lines()).hasSize(5);
        assertThat(balance.lines().subList(3, 5)).allSatisfy(line -> {
            assertThat(line.description()).isEqualTo("Deposit invoiced, No. F2026-0004 of 2 November 2026");
            assertThat(line.quantity()).isEqualByComparingTo("-1");
        });
        // 1,983.33 before VAT and 389.42 of VAT, less the deposit's 594.99 and 116.83.
        assertThat(balance.totalExcludingVat()).isEqualByComparingTo("1388.34");
        assertThat(balance.vat()).isEqualByComparingTo("272.59");
    }

    @Test
    void refusesALineOfNoQuantity() {
        assertThatThrownBy(() -> InvoiceService.lines(List.of(new InvoiceDtos.Line("Nothing", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
