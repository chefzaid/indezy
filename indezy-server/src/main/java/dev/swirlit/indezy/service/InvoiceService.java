package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceCounter;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import dev.swirlit.indezy.pdf.InvoicePdfRenderer;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InvoiceCounterRepository;
import dev.swirlit.indezy.repository.InvoiceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A workspace's invoices (see docs/features.md#invoices): drafts written by hand or made of a validated activity report,
 * issued with the next number of the year and the seller and buyer frozen as they are, then paid. An issued invoice can
 * no longer change or be deleted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final InvoiceRepository invoices;
    private final InvoiceCounterRepository counters;
    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final FreelanceRepository freelances;
    private final ActivityReportService activityReports;
    private final InvoicePdfRenderer pdf;
    private final Clock clock;
    private WebhookPublisher webhooks = WebhookPublisher.NONE;

    /** Tells the workspace's webhooks of the changes; none until Spring sets it (unit tests build the service by hand). */
    @Autowired
    void setWebhooks(WebhookPublisher webhooks) {
        this.webhooks = webhooks;
    }

    @Transactional(readOnly = true)
    public List<InvoiceDtos.Invoice> list(Long freelanceId) {
        return invoices.findByFreelanceIdOrderByCreatedAtDesc(freelanceId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.Invoice get(Long freelanceId, Long id) {
        return toDto(invoice(freelanceId, id));
    }

    /**
     * The workspace's issued invoices past their due date, the oldest due first, for the dashboard and the weekly digest,
     * each with what its issued credit notes leave due.
     */
    @Transactional(readOnly = true)
    public List<DashboardStatsDto.OverdueInvoice> overdue(Long freelanceId) {
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        return invoices.findByFreelanceIdAndStatusAndDueDateBeforeOrderByDueDateAsc(freelanceId, InvoiceStatus.ISSUED, today).stream()
            .filter(invoice -> !invoice.isCreditNote())
            .map(invoice -> new DashboardStatsDto.OverdueInvoice(invoice.getId(), invoice.getNumber(), invoice.getBuyer().getName(),
                InvoiceTotals.of(invoice.getLines()).total().subtract(creditedTotal(invoice)), invoice.getDueDate(), ChronoUnit.DAYS.between(invoice.getDueDate(), today)))
            .toList();
    }

    /** A draft written by hand, for one of the workspace's clients (and missions, when named). */
    @Transactional
    public InvoiceDtos.Invoice create(Long freelanceId, InvoiceDtos.Draft draft) {
        Invoice invoice = new Invoice();
        invoice.setFreelanceId(freelanceId);
        write(invoice, draft);
        return toDto(invoices.save(invoice));
    }

    /**
     * A draft of the month a client validated: the days worked at the mission's daily rate, billed to the ESN when the
     * mission has one, else to the client, in the language of its activity reports. One invoice per report.
     */
    @Transactional
    public InvoiceDtos.Invoice fromReport(Long freelanceId, InvoiceDtos.FromReport request) {
        YearMonth month = YearMonth.of(request.year(), request.month());
        ActivityReportDto report = activityReports.getReport(request.projectId(), month);
        if (report.getId() == null || report.getStatus() != ActivityReportStatus.VALIDATED) {
            throw new InvalidStateException("Only a validated activity report can be invoiced");
        }
        invoices.findFirstByActivityReportId(report.getId()).ifPresent(existing -> {
            throw new InvalidStateException("This activity report is already invoiced");
        });
        Project project = projects.findById(request.projectId())
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (project.getDailyRate() == null) {
            throw new InvalidStateException("The mission has no daily rate to invoice");
        }
        Client billed = project.getMiddleman() != null ? project.getMiddleman() : project.getClient();
        if (billed == null) {
            throw new InvalidStateException("The mission has no client to invoice");
        }
        DocumentLanguage language = Objects.requireNonNullElse(billed.getActivityReportLanguage(), DocumentLanguage.FR);
        BillingIdentityDto seller = BillingIdentityService.toDto(project.getFreelance());
        BigDecimal vatRate = seller.vatExempt() ? BigDecimal.ZERO : BigDecimal.valueOf(seller.vatRate());
        String period = month.getMonth().getDisplayName(TextStyle.FULL, language == DocumentLanguage.EN ? Locale.UK : Locale.FRANCE) + " " +
            month.getYear();
        String description = (language == DocumentLanguage.EN ? "Services: " : "Prestation : ") + project.getRole() + " — " + period;

        Invoice invoice = new Invoice();
        invoice.setFreelanceId(freelanceId);
        invoice.setActivityReportId(report.getId());
        invoice.setClientId(billed.getId());
        invoice.setProjectId(project.getId());
        invoice.setProjectRole(project.getRole());
        invoice.setPeriodStart(month.atDay(1));
        invoice.setPeriodEnd(month.atEndOfMonth());
        invoice.setLanguage(language);
        invoice.setLines(List.of(new InvoiceLine(description, BigDecimal.valueOf(report.getWorkedDays()).setScale(2),
            BigDecimal.valueOf(project.getDailyRate()).setScale(2), vatRate.setScale(2))));
        return toDto(invoices.save(invoice));
    }

    /**
     * A draft credit note of an issued invoice: its client, mission, period, language and lines, to reduce for a partial
     * credit. It is issued in the same numbering, and an invoice its issued credit notes cover in full reads as credited.
     */
    @Transactional
    public InvoiceDtos.Invoice creditNote(Long freelanceId, Long invoiceId) {
        Invoice original = invoice(freelanceId, invoiceId);
        if (original.isCreditNote() || original.getStatus() == InvoiceStatus.DRAFT || original.getStatus() == InvoiceStatus.CREDITED) {
            throw new InvalidStateException("Only an issued invoice not cancelled yet can be credited");
        }
        Invoice note = new Invoice();
        note.setFreelanceId(freelanceId);
        note.setKind(InvoiceKind.CREDIT_NOTE);
        note.setCreditedInvoiceId(original.getId());
        note.setCreditedNumber(original.getNumber());
        note.setCreditedIssueDate(original.getIssueDate());
        note.setClientId(original.getClientId());
        note.setProjectId(original.getProjectId());
        note.setProjectRole(original.getProjectRole());
        note.setPeriodStart(original.getPeriodStart());
        note.setPeriodEnd(original.getPeriodEnd());
        note.setLanguage(original.getLanguage());
        note.setLines(original.getLines());
        return toDto(invoices.save(note));
    }

    @Transactional
    public InvoiceDtos.Invoice update(Long freelanceId, Long id, InvoiceDtos.Draft draft) {
        Invoice invoice = draft(freelanceId, id);
        write(invoice, draft);
        return toDto(invoice);
    }

    @Transactional
    public void delete(Long freelanceId, Long id) {
        invoices.delete(draft(freelanceId, id));
    }

    /**
     * Issues a draft: the next number of the year (the workspace's counter locked meanwhile, so numbers neither skip nor
     * repeat), the issue and due dates, and the seller and buyer frozen as they are now.
     */
    @Transactional
    public InvoiceDtos.Invoice issue(Long freelanceId, Long id, InvoiceDtos.Issue request) {
        Invoice invoice = draft(freelanceId, id);
        BillingIdentityDto seller = BillingIdentityService.toDto(freelances.findById(freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found")));
        if (!Boolean.TRUE.equals(seller.complete())) {
            throw new InvalidStateException("Complete the billing identity before issuing an invoice");
        }
        Client client = clients.findById(invoice.getClientId())
            .orElseThrow(() -> new InvalidStateException("The invoice's client no longer exists"));
        if (client.getAddress() == null || client.getAddress().isBlank()) {
            throw new InvalidStateException("The client needs an address to be invoiced");
        }
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        LocalDate issued = request == null || request.issueDate() == null ? today : request.issueDate();
        if (issued.isAfter(today)) {
            throw new IllegalArgumentException("An invoice cannot be issued at a later date");
        }
        // Numbers follow the issue dates: an invoice cannot be dated before the last one issued.
        invoices.findLatestIssueDate(freelanceId).filter(issued::isBefore).ifPresent(latest -> {
            throw new InvalidStateException("An invoice cannot be dated before the last one issued, on " + latest);
        });
        Invoice credited = invoice.isCreditNote() ? creditable(freelanceId, invoice) : null;
        invoice.setNumber(nextNumber(freelanceId, issued.getYear(), seller.invoicePrefix()));
        invoice.setIssueDate(issued);
        // A credit note is not paid: it has no due date.
        invoice.setDueDate(invoice.isCreditNote() ? null : issued.plusDays(seller.paymentTermDays()));
        invoice.setSeller(seller(seller));
        invoice.setBuyer(buyer(client));
        invoice.setStatus(InvoiceStatus.ISSUED);
        if (credited != null && creditedTotal(credited).compareTo(InvoiceTotals.of(credited.getLines()).total()) >= 0) {
            credited.setStatus(InvoiceStatus.CREDITED);
        }
        log.info("Workspace {} issued invoice {}", freelanceId, invoice.getId());
        webhooks.publish(freelanceId, WebhookEvent.INVOICE_ISSUED, WebhookPayloads.invoice(invoice));
        return toDto(invoice);
    }

    /** Records the payment of an issued invoice, or takes it back. */
    @Transactional
    public InvoiceDtos.Invoice pay(Long freelanceId, Long id, InvoiceDtos.Payment payment) {
        Invoice invoice = invoice(freelanceId, id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new InvalidStateException("A draft cannot be paid: issue it first");
        }
        if (invoice.isCreditNote() || invoice.getStatus() == InvoiceStatus.CREDITED) {
            throw new InvalidStateException("A credit note, or an invoice it cancelled, is not paid");
        }
        LocalDate paidOn = payment == null ? null : payment.paidOn();
        invoice.setPaidOn(paidOn);
        invoice.setStatus(paidOn == null ? InvoiceStatus.ISSUED : InvoiceStatus.PAID);
        if (paidOn != null) {
            webhooks.publish(freelanceId, WebhookEvent.INVOICE_PAID, WebhookPayloads.invoice(invoice));
        }
        return toDto(invoice);
    }

    /** The invoice's PDF: as issued, or, for a draft, with the seller and the client as they are now. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile renderPdf(Long freelanceId, Long id) {
        Invoice invoice = invoice(freelanceId, id);
        boolean draft = invoice.getStatus() == InvoiceStatus.DRAFT;
        InvoiceSeller seller = draft ? seller(BillingIdentityService.toDto(freelances.findById(freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found")))) : invoice.getSeller();
        InvoiceBuyer buyer = draft ? clients.findById(invoice.getClientId()).map(InvoiceService::buyer).orElseGet(InvoiceBuyer::new) :
            invoice.getBuyer();
        boolean english = invoice.getLanguage() == DocumentLanguage.EN;
        String name = invoice.isCreditNote() ? (english ? "credit-note" : "avoir") : (english ? "invoice" : "facture");
        String fileName = name + "-" + (draft ? (invoice.getLanguage() == DocumentLanguage.EN ? "draft-" : "brouillon-") + invoice.getId() :
            invoice.getNumber()) + ".pdf";
        return new ActivityReportService.PdfFile(fileName, pdf.render(invoice, seller, buyer));
    }

    /** The invoice a credit note credits, refused when the note would credit more than what is left of it. */
    private Invoice creditable(Long freelanceId, Invoice note) {
        Invoice credited = invoice(freelanceId, note.getCreditedInvoiceId());
        if (credited.getStatus() == InvoiceStatus.CREDITED) {
            throw new InvalidStateException("The invoice is already cancelled by its credit notes");
        }
        BigDecimal left = InvoiceTotals.of(credited.getLines()).total().subtract(creditedTotal(credited));
        if (InvoiceTotals.of(note.getLines()).total().compareTo(left) > 0) {
            throw new InvalidStateException("A credit note cannot credit more than what is left of its invoice: " + left);
        }
        return credited;
    }

    /** What the issued credit notes of an invoice credit, in all. */
    private BigDecimal creditedTotal(Invoice credited) {
        return invoices.findByCreditedInvoiceIdAndNumberIsNotNull(credited.getId()).stream()
            .map(note -> InvoiceTotals.of(note.getLines()).total()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String nextNumber(Long freelanceId, int year, String prefix) {
        InvoiceCounter counter = counters.findByFreelanceIdAndYear(freelanceId, year).orElseGet(() -> {
            InvoiceCounter created = new InvoiceCounter();
            created.setFreelanceId(freelanceId);
            created.setYear(year);
            return counters.saveAndFlush(created);
        });
        counter.setLastNumber(counter.getLastNumber() + 1);
        return String.format("%s%d-%04d", prefix, year, counter.getLastNumber());
    }

    private void write(Invoice invoice, InvoiceDtos.Draft draft) {
        if (invoice.isCreditNote() && !invoice.getClientId().equals(draft.clientId())) {
            throw new InvalidStateException("A credit note goes to the client of its invoice");
        }
        Client client = clients.findById(draft.clientId()).filter(found -> found.getFreelance() != null &&
                invoice.getFreelanceId().equals(found.getFreelance().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        invoice.setClientId(client.getId());
        if (draft.projectId() != null) {
            Project project = projects.findById(draft.projectId()).filter(found -> found.getFreelance() != null &&
                    invoice.getFreelanceId().equals(found.getFreelance().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
            invoice.setProjectId(project.getId());
            invoice.setProjectRole(project.getRole());
        } else if (invoice.getActivityReportId() == null) {
            invoice.setProjectId(null);
            invoice.setProjectRole(null);
        }
        if (draft.periodStart() != null && draft.periodEnd() != null && draft.periodEnd().isBefore(draft.periodStart())) {
            throw new IllegalArgumentException("The period ends before it starts");
        }
        invoice.setPeriodStart(draft.periodStart());
        invoice.setPeriodEnd(draft.periodEnd());
        invoice.setLanguage(Objects.requireNonNullElse(draft.language(), DocumentLanguage.FR));
        invoice.setNotes(draft.notes() == null || draft.notes().isBlank() ? null : draft.notes().strip());
        invoice.setLines(draft.lines().stream().map(line -> new InvoiceLine(line.description().strip(), line.quantity().setScale(2),
            line.unitPrice().setScale(2), line.vatRate().setScale(2))).toList());
    }

    private Invoice invoice(Long freelanceId, Long id) {
        return invoices.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    private Invoice draft(Long freelanceId, Long id) {
        Invoice invoice = invoice(freelanceId, id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvalidStateException("An issued invoice can no longer change");
        }
        return invoice;
    }

    private static InvoiceSeller seller(BillingIdentityDto identity) {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName(identity.legalName());
        seller.setLegalForm(identity.legalForm());
        seller.setSiret(identity.siret());
        seller.setVatNumber(identity.vatNumber());
        seller.setVatExempt(identity.vatExempt());
        seller.setShareCapital(identity.shareCapital());
        seller.setRegistration(identity.registration());
        seller.setAddress(identity.address());
        seller.setIban(identity.iban());
        seller.setBic(identity.bic());
        return seller;
    }

    private static InvoiceBuyer buyer(Client client) {
        InvoiceBuyer buyer = new InvoiceBuyer();
        buyer.setName(client.getCompanyName());
        buyer.setAddress(Stream.of(client.getAddress(), client.getCity()).filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining("\n")));
        buyer.setSiren(client.getSiren());
        buyer.setVatNumber(client.getVatNumber());
        return buyer;
    }

    private InvoiceDtos.Invoice toDto(Invoice invoice) {
        InvoiceTotals totals = InvoiceTotals.of(invoice.getLines());
        String clientName = invoice.getBuyer() != null && invoice.getBuyer().getName() != null ? invoice.getBuyer().getName() :
            clients.findById(invoice.getClientId()).map(Client::getCompanyName).orElse(null);
        boolean overdue = !invoice.isCreditNote() && invoice.getStatus() == InvoiceStatus.ISSUED && invoice.getDueDate() != null &&
            invoice.getDueDate().isBefore(LocalDate.now(clock.withZone(PARIS)));
        return new InvoiceDtos.Invoice(invoice.getId(), invoice.isCreditNote() ? InvoiceKind.CREDIT_NOTE : InvoiceKind.INVOICE,
            invoice.getNumber(), invoice.getStatus(), overdue, invoice.getCreditedInvoiceId(), invoice.getCreditedNumber(), invoice.getClientId(), clientName,
            invoice.getProjectId(), invoice.getProjectRole(), invoice.getActivityReportId(), invoice.getIssueDate(), invoice.getDueDate(),
            invoice.getPeriodStart(), invoice.getPeriodEnd(), invoice.getLanguage(), invoice.getNotes(), invoice.getLines().stream()
            .map(line -> new InvoiceDtos.Line(line.getDescription(), line.getQuantity(), line.getUnitPrice(), line.getVatRate())).toList(),
            totals.excludingVat(), totals.vat(), totals.total(), invoice.getPaidOn(), invoice.getCreatedAt());
    }
}
