package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceCurrency;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Invoices as listed and shown, and the requests that write, issue and settle them. */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /**
     * A line: days (or any quantity) at a price before VAT, with its VAT rate in percent (0 when exempt). A negative
     * quantity takes off, as a discount or a deposit already invoiced; the price itself is never negative (rule BR-27 of
     * EN 16931), and a quantity is never zero.
     */
    public record Line(
        @NotBlank @Size(max = 300) String description,
        @NotNull @DecimalMin("-9999") @DecimalMax("9999") @Digits(integer = 4, fraction = 2) BigDecimal quantity,
        @NotNull @DecimalMin("0") @DecimalMax("999999") @Digits(integer = 6, fraction = 2) BigDecimal unitPrice,
        @NotNull @DecimalMin("0") @DecimalMax("30") @Digits(integer = 2, fraction = 2) BigDecimal vatRate) {
    }

    public record Invoice(
        Long id, InvoiceKind kind, String number, InvoiceStatus status, boolean overdue, Long creditedInvoiceId, String creditedNumber,
        Long quoteId, String quoteNumber, Long clientId, String clientName, Long projectId,
        String projectRole, Long activityReportId, LocalDate issueDate, LocalDate dueDate, LocalDate periodStart, LocalDate periodEnd,
        DocumentLanguage language, String notes, List<Line> lines, BigDecimal totalExcludingVat, BigDecimal vat, BigDecimal total,
        LocalDate paidOn, LocalDateTime createdAt, LateFees lateFees, InvoiceCurrency currency, BigDecimal exchangeRate,
        BigDecimal totalExcludingVatInEuros, BigDecimal totalInEuros, LocalDate remindedOn) {

        public Invoice {
            lines = List.copyOf(lines);
        }
    }

    /**
     * What an overdue invoice is owed on top of its amount (art. L441-10 du Code de commerce): the interest at the rate
     * the invoice stated, from the day after its due date to today ({@code interest} null when it stated no figure), and
     * the fixed €40 recovery indemnity.
     */
    public record LateFees(long daysLate, BigDecimal amountDue, Double rate, BigDecimal interest, BigDecimal indemnity) {
    }

    /**
     * A draft written by hand: the client, optionally its mission, the period, the language, notes and lines; its
     * currency (the euro when absent) and, in another currency, the euros one unit is worth at the issue date.
     */
    public record Draft(
        @NotNull Long clientId,
        Long projectId,
        LocalDate periodStart,
        LocalDate periodEnd,
        DocumentLanguage language,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines,
        InvoiceCurrency currency,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 6) BigDecimal exchangeRate) {

        public Draft {
            lines = lines == null ? null : List.copyOf(lines);
        }
    }

    /**
     * A month's activity report that can be invoiced: its mission, whom it bills (null without a client), its status, the
     * days worked, the mission's daily rate and their amount before VAT (null without a rate).
     */
    public record InvoiceableReport(Long reportId, Long projectId, String projectRole, String clientName, int year, int month,
                                    ActivityReportStatus status, BigDecimal workedDays, Integer dailyRate, BigDecimal amount) {
    }

    /** A draft made of a month's activity report: the mission and the month. */
    public record FromReport(@NotNull Long projectId, @NotNull Integer year, @NotNull Integer month) {
    }

    /** The day an invoice is issued (today when absent). */
    public record Issue(LocalDate issueDate) {
    }

    /** The day it was paid; absent to say it is not paid after all. */
    public record Payment(LocalDate paidOn) {
    }

    /** A bank statement exported as CSV, to match its credits with the invoices awaiting payment. */
    public record Statement(@NotBlank @Size(max = 2_000_000) String content) {
    }

    /** How a client pays: over how many paid invoices, the average days from issue to payment and past the due date. */
    public record PaymentDelay(Long clientId, int paidInvoices, long averageDays, long averageDaysLate) {
    }
}
