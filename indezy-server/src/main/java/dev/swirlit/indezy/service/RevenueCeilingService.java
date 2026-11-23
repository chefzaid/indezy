package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InvoiceRepository;
import dev.swirlit.indezy.tax.FrenchFigures;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The year's revenue against the micro-entreprise ceiling and the VAT franchise thresholds for services (see
 * docs/features.md#revenue-ceilings). Both count the revenue received, excluding VAT, on the day it was paid: the paid
 * invoices less the credit notes issued on them by then, in euros.
 */
@Service
@RequiredArgsConstructor
public class RevenueCeilingService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** From this share of a ceiling, it is said to be near. */
    static final int NEAR_PERCENT = 80;

    private final InvoiceRepository invoices;
    private final FreelanceRepository freelances;
    private final Clock clock;

    /** Where the revenue stands against a ceiling. */
    public enum Level {
        UNDER,
        /** From {@value #NEAR_PERCENT} % of the ceiling. */
        NEAR,
        /** Past the micro-entreprise ceiling one year: a second year in a row ends the regime. */
        OVER,
        /** The regime or the franchise ends on {@link Ceiling#endsOn()}. */
        ENDS
    }

    /**
     * One ceiling: its amount (and the franchise's higher threshold), the revenue it is measured against, its share in
     * percent, where it stands, and the day the regime or the franchise ends when it does.
     */
    public record Ceiling(BigDecimal limit, BigDecimal tolerance, BigDecimal revenue, int percent, Level level, LocalDate endsOn) {
    }

    /**
     * The year's ceilings: the figures' year (the latest known when the year has none), the revenue received this year
     * and the year before, what issued invoices still owe, and each ceiling that applies (the micro-entreprise one for a
     * micro-entreprise, the franchise's while exempt from VAT), else null.
     */
    public record Ceilings(int year, int figuresYear, LegalForm legalForm, boolean vatExempt, BigDecimal received,
                           BigDecimal receivedLastYear, BigDecimal outstanding, Ceiling micro, Ceiling vat) {
    }

    @Transactional(readOnly = true)
    public Ceilings ceilings(Long freelanceId, Integer asked) {
        int year = asked != null ? asked : LocalDate.now(clock.withZone(PARIS)).getYear();
        BillingIdentity billing = freelances.findById(freelanceId).map(Freelance::getBilling).orElse(null);
        LegalForm form = billing == null ? null : billing.getLegalForm();
        boolean exempt = billing == null || !Boolean.FALSE.equals(billing.getVatExempt());
        List<Invoice> all = invoices.findByFreelanceIdOrderByCreatedAtDesc(freelanceId);
        Map<Long, List<Invoice>> notes = all.stream().filter(invoice -> invoice.isCreditNote() && invoice.getNumber() != null &&
                invoice.getCreditedInvoiceId() != null)
            .collect(Collectors.groupingBy(Invoice::getCreditedInvoiceId));
        List<Payment> payments = all.stream().filter(invoice -> !invoice.isCreditNote() && invoice.getStatus() == InvoiceStatus.PAID &&
                invoice.getPaidOn() != null)
            .map(invoice -> new Payment(invoice.getPaidOn(), net(invoice, notes, invoice.getPaidOn())))
            .sorted(Comparator.comparing(Payment::on)).toList();
        BigDecimal received = sum(payments, year);
        BigDecimal lastYear = sum(payments, year - 1);
        BigDecimal outstanding = all.stream().filter(invoice -> !invoice.isCreditNote() && invoice.getStatus() == InvoiceStatus.ISSUED)
            .map(invoice -> net(invoice, notes, null)).reduce(BigDecimal.ZERO, BigDecimal::add);
        FrenchFigures.Year figures = FrenchFigures.of(year);
        return new Ceilings(year, figures.year(), form, exempt, received, lastYear, outstanding,
            form == LegalForm.MICRO_ENTREPRISE ? micro(year, received, lastYear) : null,
            exempt ? vat(year, payments, received, lastYear) : null);
    }

    private record Payment(LocalDate on, BigDecimal amount) {
    }

    /** Past the ceiling two years in a row, the micro-entreprise regime ends on the next 1 January. */
    static Ceiling micro(int year, BigDecimal received, BigDecimal lastYear) {
        BigDecimal limit = FrenchFigures.of(year).microServicesCeiling();
        int percent = percent(received, limit);
        if (received.compareTo(limit) > 0) {
            boolean twice = lastYear.compareTo(FrenchFigures.of(year - 1).microServicesCeiling()) > 0;
            return new Ceiling(limit, null, received, percent, twice ? Level.ENDS : Level.OVER, twice ? LocalDate.of(year + 1, 1, 1) : null);
        }
        return new Ceiling(limit, null, received, percent, percent >= NEAR_PERCENT ? Level.NEAR : Level.UNDER, null);
    }

    /**
     * The franchise ends on 1 January when the year before passed its base threshold, on the day the year passes the
     * higher one, or on the next 1 January when the year passes the base threshold only.
     */
    private static Ceiling vat(int year, List<Payment> payments, BigDecimal received, BigDecimal lastYear) {
        FrenchFigures.Year figures = FrenchFigures.of(year);
        BigDecimal base = figures.vatServicesBase();
        BigDecimal tolerance = figures.vatServicesTolerance();
        int percent = percent(received, base);
        if (lastYear.compareTo(FrenchFigures.of(year - 1).vatServicesBase()) > 0) {
            return new Ceiling(base, tolerance, received, percent, Level.ENDS, LocalDate.of(year, 1, 1));
        }
        BigDecimal running = BigDecimal.ZERO;
        for (Payment payment : payments) {
            if (payment.on().getYear() == year) {
                running = running.add(payment.amount());
                if (running.compareTo(tolerance) > 0) {
                    return new Ceiling(base, tolerance, received, percent, Level.ENDS, payment.on());
                }
            }
        }
        if (received.compareTo(base) > 0) {
            return new Ceiling(base, tolerance, received, percent, Level.ENDS, LocalDate.of(year + 1, 1, 1));
        }
        return new Ceiling(base, tolerance, received, percent, percent >= NEAR_PERCENT ? Level.NEAR : Level.UNDER, null);
    }

    private static BigDecimal sum(List<Payment> payments, int year) {
        return payments.stream().filter(payment -> payment.on().getYear() == year).map(Payment::amount)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** The invoice's amount before VAT in euros, less its credit notes (those issued by {@code by} when given), never below zero. */
    private static BigDecimal net(Invoice invoice, Map<Long, List<Invoice>> notes, LocalDate by) {
        BigDecimal amount = euros(invoice);
        for (Invoice note : notes.getOrDefault(invoice.getId(), List.of())) {
            if (by == null || note.getIssueDate() == null || !note.getIssueDate().isAfter(by)) {
                amount = amount.subtract(euros(note));
            }
        }
        return amount.signum() < 0 ? BigDecimal.ZERO.setScale(2) : amount;
    }

    private static BigDecimal euros(Invoice invoice) {
        InvoiceTotals totals = Objects.requireNonNullElse(InvoiceTotals.inEuros(invoice), InvoiceTotals.of(invoice.getLines()));
        return totals.excludingVat();
    }

    private static int percent(BigDecimal revenue, BigDecimal limit) {
        return revenue.multiply(HUNDRED).divide(limit, 0, RoundingMode.FLOOR).intValue();
    }
}
