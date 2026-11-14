package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.QuoteStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Quotes as listed and shown, and the requests that write, send and settle them. */
public final class QuoteDtos {

    private QuoteDtos() {
    }

    /** A quote; a sent one past its validity reads as expired, and an accepted one names its deposit and final invoices. */
    public record Quote(
        Long id, String number, QuoteStatus status, boolean expired, Long clientId, String clientName, Long projectId, String projectRole,
        LocalDate issueDate, int validityDays, LocalDate validUntil, Integer depositPercent, DocumentLanguage language, String notes,
        List<InvoiceDtos.Line> lines, BigDecimal totalExcludingVat, BigDecimal vat, BigDecimal total, LocalDate decidedOn, Long invoiceId,
        Long depositInvoiceId,
        LocalDateTime createdAt) {

        public Quote {
            lines = List.copyOf(lines);
        }
    }

    /** A draft: the client, optionally its mission, the language, how long it holds, the deposit asked, notes and lines. */
    public record Draft(
        @NotNull Long clientId,
        Long projectId,
        DocumentLanguage language,
        @Min(1) @Max(365) Integer validityDays,
        @Min(1) @Max(100) Integer depositPercent,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 50) List<InvoiceDtos.@Valid Line> lines) {

        public Draft {
            lines = lines == null ? null : List.copyOf(lines);
        }
    }

    /** The day a quote is sent (today when absent). */
    public record Send(LocalDate issueDate) {
    }

    /** The client's answer to a sent quote. */
    public record Decision(@NotNull Boolean accepted) {
    }
}
