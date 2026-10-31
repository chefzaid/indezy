package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How each client of the workspace really pays (see docs/features.md#invoices): from its paid invoices, the average
 * days from issue to payment and past the due date (none when paid on time or early).
 */
@Service
@RequiredArgsConstructor
public class InvoicePaymentDelays {

    private final InvoiceRepository invoices;

    @Transactional(readOnly = true)
    public List<InvoiceDtos.PaymentDelay> byClient(Long freelanceId) {
        Map<Long, List<Invoice>> paid = invoices.findByFreelanceIdAndStatus(freelanceId, InvoiceStatus.PAID).stream()
            .filter(invoice -> !invoice.isCreditNote() && invoice.getIssueDate() != null && invoice.getPaidOn() != null)
            .collect(Collectors.groupingBy(Invoice::getClientId));
        return paid.entrySet().stream()
            .map(entry -> new InvoiceDtos.PaymentDelay(entry.getKey(), entry.getValue().size(),
                Math.round(entry.getValue().stream().mapToLong(invoice -> ChronoUnit.DAYS.between(invoice.getIssueDate(), invoice.getPaidOn()))
                    .average().orElse(0)),
                Math.round(entry.getValue().stream().mapToLong(InvoicePaymentDelays::daysLate).average().orElse(0))))
            .sorted(Comparator.comparing(InvoiceDtos.PaymentDelay::clientId))
            .toList();
    }

    private static long daysLate(Invoice invoice) {
        return invoice.getDueDate() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(invoice.getDueDate(), invoice.getPaidOn()));
    }
}
