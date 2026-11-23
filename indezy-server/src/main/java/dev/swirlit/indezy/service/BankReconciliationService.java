package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.util.BankStatementCsv;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Matches a bank statement's credits with the invoices awaiting payment (see docs/features.md#bank-reconciliation),
 * without a bank connection: the statement is the CSV export the bank gives. Nothing is recorded: the freelancer ticks
 * the matches to keep, each then marked paid on its credit's day like any invoice. Each invoice matches one credit at
 * most, by the strongest reason first: its number in the label, then its client's name in the label with the same
 * amount, then the same amount alone when no other invoice awaits it. A credit before the invoice's issue never matches.
 */
@Service
@RequiredArgsConstructor
public class BankReconciliationService {

    /** Why a credit was matched to an invoice, the strongest first. */
    public enum Reason {
        NUMBER,
        CLIENT,
        AMOUNT
    }

    /** A credit of the statement, with the invoice it pays when one is found (and why), else none. */
    public record Match(LocalDate date, String label, BigDecimal amount, Long invoiceId, String invoiceNumber, String clientName,
                        Reason reason) {
    }

    private final InvoiceService invoices;

    @Transactional(readOnly = true)
    public List<Match> match(Long freelanceId, String statement) {
        List<BankStatementCsv.Credit> credits = BankStatementCsv.credits(statement);
        List<InvoiceDtos.Invoice> awaiting = invoices.list(freelanceId).stream()
            .filter(invoice -> invoice.status() == InvoiceStatus.ISSUED && invoice.kind() != InvoiceKind.CREDIT_NOTE && invoice.number() != null)
            .toList();
        InvoiceDtos.Invoice[] chosen = new InvoiceDtos.Invoice[credits.size()];
        Reason[] reasons = new Reason[credits.size()];
        Set<Long> used = new HashSet<>();
        for (Reason reason : Reason.values()) {
            for (int index = 0; index < credits.size(); index++) {
                if (chosen[index] != null) {
                    continue;
                }
                BankStatementCsv.Credit credit = credits.get(index);
                List<InvoiceDtos.Invoice> fitting = awaiting.stream().filter(invoice -> !used.contains(invoice.id()))
                    .filter(invoice -> invoice.issueDate() == null || !credit.date().isBefore(invoice.issueDate()))
                    .filter(invoice -> fits(invoice, credit, reason)).toList();
                boolean single = reason != Reason.AMOUNT || fitting.size() == 1 && awaiting.stream()
                    .filter(invoice -> !used.contains(invoice.id()) && sameAmount(invoice, credit.amount())).count() == 1;
                if (!fitting.isEmpty() && single) {
                    chosen[index] = fitting.getFirst();
                    reasons[index] = reason;
                    used.add(fitting.getFirst().id());
                }
            }
        }
        List<Match> matches = new ArrayList<>();
        for (int index = 0; index < credits.size(); index++) {
            BankStatementCsv.Credit credit = credits.get(index);
            InvoiceDtos.Invoice invoice = chosen[index];
            matches.add(invoice == null ? new Match(credit.date(), credit.label(), credit.amount(), null, null, null, null) :
                new Match(credit.date(), credit.label(), credit.amount(), invoice.id(), invoice.number(), invoice.clientName(), reasons[index]));
        }
        return matches;
    }

    private static boolean fits(InvoiceDtos.Invoice invoice, BankStatementCsv.Credit credit, Reason reason) {
        return switch (reason) {
            case NUMBER -> sameAmount(invoice, credit.amount()) && compact(credit.label()).contains(compact(invoice.number()));
            case CLIENT -> sameAmount(invoice, credit.amount()) && namesClient(credit.label(), invoice.clientName());
            default -> sameAmount(invoice, credit.amount());
        };
    }

    /** The amount the bank receives: the total, in euros for an invoice in another currency. */
    private static boolean sameAmount(InvoiceDtos.Invoice invoice, BigDecimal amount) {
        BigDecimal total = invoice.totalInEuros() != null ? invoice.totalInEuros() : invoice.total();
        return total != null && total.compareTo(amount) == 0;
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("[\\s\\-_/.]", "").toUpperCase(Locale.ROOT);
    }

    /** Whether the label holds the client's name, or each of its words of three letters or more. */
    private static boolean namesClient(String label, String clientName) {
        if (clientName == null || clientName.isBlank()) {
            return false;
        }
        String words = " " + TextFolding.words(label) + " ";
        List<String> name = Arrays.stream(TextFolding.words(clientName).split(" ")).filter(word -> word.length() >= 3)
            .filter(word -> !Set.of("sas", "sarl", "eurl", "sasu", "the", "les", "des").contains(word)).toList();
        return !name.isEmpty() && name.stream().allMatch(word -> words.contains(" " + word + " "));
    }
}
