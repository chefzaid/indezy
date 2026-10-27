package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
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

    /** A line: days (or any quantity) at a price before VAT, with its VAT rate in percent (0 when exempt). */
    public record Line(
        @NotBlank @Size(max = 300) String description,
        @NotNull @DecimalMin("0.01") @DecimalMax("9999") @Digits(integer = 4, fraction = 2) BigDecimal quantity,
        @NotNull @DecimalMin("0") @DecimalMax("999999") @Digits(integer = 6, fraction = 2) BigDecimal unitPrice,
        @NotNull @DecimalMin("0") @DecimalMax("30") @Digits(integer = 2, fraction = 2) BigDecimal vatRate) {
    }

    public record Invoice(
        Long id, String number, InvoiceStatus status, boolean overdue, Long clientId, String clientName, Long projectId,
        String projectRole, Long activityReportId, LocalDate issueDate, LocalDate dueDate, LocalDate periodStart, LocalDate periodEnd,
        DocumentLanguage language, String notes, List<Line> lines, BigDecimal totalExcludingVat, BigDecimal vat, BigDecimal total,
        LocalDate paidOn, LocalDateTime createdAt) {

        public Invoice {
            lines = List.copyOf(lines);
        }
    }

    /** A draft written by hand: the client, optionally its mission, the period, the language, notes and lines. */
    public record Draft(
        @NotNull Long clientId,
        Long projectId,
        LocalDate periodStart,
        LocalDate periodEnd,
        DocumentLanguage language,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines) {

        public Draft {
            lines = lines == null ? null : List.copyOf(lines);
        }
    }

    /** A draft made of a validated activity report: the mission and the month. */
    public record FromReport(@NotNull Long projectId, @NotNull Integer year, @NotNull Integer month) {
    }

    /** The day an invoice is issued (today when absent). */
    public record Issue(LocalDate issueDate) {
    }

    /** The day it was paid; absent to say it is not paid after all. */
    public record Payment(LocalDate paidOn) {
    }
}
