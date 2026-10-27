package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InvoiceRepository;
import dev.swirlit.indezy.util.CsvText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A year of a workspace's invoices for its accountant (see docs/features.md#accounting-export): the journal entries in
 * the FEC format that French accounting tools import, or the register of invoices and credit notes as a spreadsheet.
 * Only issued invoices and credit notes count; drafts are not accounting documents.
 */
@Service
@RequiredArgsConstructor
public class AccountingExportService {

    /** What the export holds: journal entries (FEC) or the list of invoices (register). */
    public enum Format { FEC, REGISTER }

    /** A file to download. */
    public record ExportFile(String fileName, String contentType, byte[] content) {
        public ExportFile {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private static final String CRLF = "\r\n";
    private static final DateTimeFormatter FEC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter FRENCH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] FEC_COLUMNS = {"JournalCode", "JournalLib", "EcritureNum", "EcritureDate", "CompteNum", "CompteLib",
        "CompAuxNum", "CompAuxLib", "PieceRef", "PieceDate", "EcritureLib", "Debit", "Credit", "EcritureLet", "DateLet", "ValidDate",
        "Montantdevise", "Idevise"};
    private static final String[] REGISTER_FR = {"Numéro", "Type", "Date", "Client", "SIREN", "N° TVA", "Mission", "Total HT", "TVA",
        "Total TTC", "Échéance", "Payée le", "Statut", "Facture créditée"};
    private static final String[] REGISTER_EN = {"Number", "Type", "Date", "Client", "SIREN", "VAT number", "Mission", "Excluding VAT",
        "VAT", "Total", "Due date", "Paid on", "Status", "Credited invoice"};

    private static final String CLIENTS = "411000";
    private static final String SERVICES = "706000";
    private static final String VAT_COLLECTED = "445710";
    private static final String BANK = "512000";

    private final InvoiceRepository invoices;
    private final FreelanceRepository freelances;

    @Transactional(readOnly = true)
    public ExportFile export(Long freelanceId, int year, Format format, DocumentLanguage language) {
        List<Invoice> issued = invoices.findByFreelanceIdOrderByCreatedAtDesc(freelanceId).stream()
            .filter(invoice -> invoice.getNumber() != null && invoice.getIssueDate() != null).toList();
        if (format == Format.FEC) {
            return new ExportFile(fecName(freelanceId, year), "text/plain; charset=UTF-8", fec(issued, year).getBytes(StandardCharsets.UTF_8));
        }
        boolean english = language == DocumentLanguage.EN;
        return new ExportFile((english ? "invoices-" : "factures-") + year + ".csv", "text/csv; charset=UTF-8",
            ("﻿" + register(issued, year, english)).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The year's entries, numbered in date order: each invoice in the sales journal (the client debited with the total,
     * the services and the VAT credited), each credit note the other way round, and each payment in the bank journal.
     */
    private String fec(List<Invoice> issued, int year) {
        Map<Long, List<Invoice>> notesByInvoice = issued.stream().filter(Invoice::isCreditNote)
            .collect(Collectors.groupingBy(Invoice::getCreditedInvoiceId));
        List<Entry> entries = new ArrayList<>();
        for (Invoice invoice : issued) {
            InvoiceTotals totals = InvoiceTotals.of(invoice.getLines());
            String client = name(invoice.getBuyer());
            if (invoice.getIssueDate().getYear() == year) {
                boolean note = invoice.isCreditNote();
                List<Posting> postings = new ArrayList<>();
                postings.add(Posting.of(CLIENTS, "Clients", invoice, totals.total(), note));
                postings.add(Posting.of(SERVICES, "Prestations de services", null, totals.excludingVat(), !note));
                if (totals.vat().signum() != 0) {
                    postings.add(Posting.of(VAT_COLLECTED, "TVA collectée", null, totals.vat(), !note));
                }
                entries.add(new Entry(invoice.getIssueDate(), "VE", invoice.getNumber(), invoice.getIssueDate(),
                    (note ? "Avoir " : "Facture ") + invoice.getNumber() + " " + client, postings));
            }
            LocalDate paidOn = invoice.getPaidOn();
            if (!invoice.isCreditNote() && paidOn != null && paidOn.getYear() == year) {
                // What was paid: the invoice less the credit notes issued by then.
                BigDecimal paid = notesByInvoice.getOrDefault(invoice.getId(), List.of()).stream()
                    .filter(note -> !note.getIssueDate().isAfter(paidOn))
                    .map(note -> InvoiceTotals.of(note.getLines()).total()).reduce(totals.total(), BigDecimal::subtract);
                if (paid.signum() > 0) {
                    entries.add(new Entry(paidOn, "BQ", invoice.getNumber(), paidOn, "Règlement " + invoice.getNumber() + " " + client,
                        List.of(Posting.of(BANK, "Banque", null, paid, false), Posting.of(CLIENTS, "Clients", invoice, paid, true))));
                }
            }
        }
        entries.sort(Comparator.comparing(Entry::date).thenComparing(Entry::journal, Comparator.reverseOrder()).thenComparing(Entry::piece));

        StringBuilder text = new StringBuilder(String.join("\t", FEC_COLUMNS)).append(CRLF);
        int number = 0;
        for (Entry entry : entries) {
            number++;
            for (Posting posting : entry.postings()) {
                String date = entry.date().format(FEC_DATE);
                text.append(String.join("\t", entry.journal(), "VE".equals(entry.journal()) ? "Ventes" : "Banque", String.valueOf(number),
                    date, posting.account(), posting.label(), posting.auxiliary(), fecText(posting.auxiliaryLabel()), entry.piece(),
                    entry.pieceDate().format(FEC_DATE), fecText(entry.label()), fecAmount(posting.debit()), fecAmount(posting.credit()),
                    "", "", date, "", "")).append(CRLF);
            }
        }
        return text.toString();
    }

    /** The year's invoices and credit notes, a credit note's amounts negative, then their totals. */
    private String register(List<Invoice> issued, int year, boolean english) {
        String separator = english ? "," : ";";
        StringBuilder csv = new StringBuilder(CsvText.row(separator, english ? REGISTER_EN : REGISTER_FR)).append(CRLF);
        BigDecimal[] sums = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        List<Invoice> rows = issued.stream().filter(invoice -> invoice.getIssueDate().getYear() == year)
            .sorted(Comparator.comparing(Invoice::getIssueDate).thenComparing(Invoice::getNumber)).toList();
        for (Invoice invoice : rows) {
            InvoiceTotals totals = InvoiceTotals.of(invoice.getLines());
            BigDecimal sign = invoice.isCreditNote() ? BigDecimal.ONE.negate() : BigDecimal.ONE;
            BigDecimal[] amounts = {totals.excludingVat().multiply(sign), totals.vat().multiply(sign), totals.total().multiply(sign)};
            for (int i = 0; i < sums.length; i++) {
                sums[i] = sums[i].add(amounts[i]);
            }
            InvoiceBuyer buyer = Objects.requireNonNullElseGet(invoice.getBuyer(), InvoiceBuyer::new);
            csv.append(CsvText.row(separator, invoice.getNumber(), kind(invoice, english), date(invoice.getIssueDate(), english),
                CsvText.text(buyer.getName()), CsvText.text(buyer.getSiren()), CsvText.text(buyer.getVatNumber()),
                CsvText.text(invoice.getProjectRole()), amount(amounts[0], english), amount(amounts[1], english), amount(amounts[2], english),
                date(invoice.getDueDate(), english), date(invoice.getPaidOn(), english), status(invoice, english),
                Objects.requireNonNullElse(invoice.getCreditedNumber(), ""))).append(CRLF);
        }
        csv.append(CsvText.row(separator, "Total", "", "", "", "", "", "", amount(sums[0], english), amount(sums[1], english),
            amount(sums[2], english), "", "", "", "")).append(CRLF);
        return csv.toString();
    }

    /** {@code <SIREN>FEC<closing date>.txt}, as the tax administration names it, or without the SIREN until it is known. */
    private String fecName(Long freelanceId, int year) {
        String siret = freelances.findById(freelanceId).map(Freelance::getBilling).map(BillingIdentity::getSiret).orElse(null);
        return (siret != null && siret.length() >= 9 ? siret.substring(0, 9) : "") + "FEC" + year + "1231.txt";
    }

    private static String kind(Invoice invoice, boolean english) {
        if (invoice.isCreditNote()) {
            return english ? "Credit note" : "Avoir";
        }
        return english ? "Invoice" : "Facture";
    }

    private static String status(Invoice invoice, boolean english) {
        if (invoice.isCreditNote()) {
            return "";
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return english ? "Paid" : "Payée";
        }
        if (invoice.getStatus() == InvoiceStatus.CREDITED) {
            return english ? "Credited" : "Annulée par avoir";
        }
        return english ? "Awaiting payment" : "En attente de paiement";
    }

    private static String name(InvoiceBuyer buyer) {
        return buyer == null || buyer.getName() == null ? "" : buyer.getName();
    }

    private static String date(LocalDate date, boolean english) {
        if (date == null) {
            return "";
        }
        return english ? date.toString() : date.format(FRENCH_DATE);
    }

    private static String amount(BigDecimal amount, boolean english) {
        String plain = amount.setScale(2).toPlainString();
        return english ? plain : plain.replace('.', ',');
    }

    /** FEC amounts: a decimal comma, and zero on the side not used. */
    private static String fecAmount(BigDecimal amount) {
        return amount == null ? "0,00" : amount.setScale(2).toPlainString().replace('.', ',');
    }

    /** FEC fields are separated by tabs and end at a line break, so neither may appear inside one. */
    private static String fecText(String value) {
        return value == null ? "" : value.replaceAll("[\\t\\r\\n]+", " ").strip();
    }

    private record Entry(LocalDate date, String journal, String piece, LocalDate pieceDate, String label, List<Posting> postings) {
        Entry {
            postings = List.copyOf(postings);
        }
    }

    /** One line of an entry: an account debited or credited, with the client's own account for the client lines. */
    private record Posting(String account, String label, String auxiliary, String auxiliaryLabel, BigDecimal debit, BigDecimal credit) {

        static Posting of(String account, String label, Invoice client, BigDecimal amount, boolean credit) {
            String auxiliary = client == null ? "" : String.format("C%06d", client.getClientId());
            String auxiliaryLabel = client == null ? "" : name(client.getBuyer());
            return new Posting(account, label, auxiliary, auxiliaryLabel, credit ? null : amount, credit ? amount : null);
        }
    }
}
