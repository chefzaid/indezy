package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.dto.QuoteDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.DocumentTermsFile;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.QuoteCounter;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DocumentTemplateKind;
import dev.swirlit.indezy.model.enums.InvoiceKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.QuoteStatus;
import dev.swirlit.indezy.pdf.QuotePdfRenderer;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.DocumentTermsFileRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InvoiceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.QuoteCounterRepository;
import dev.swirlit.indezy.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A workspace's quotes (see docs/features.md#quotes): drafts, sent with the next quote number of the year and the
 * seller and buyer frozen as they are, accepted or refused by the client, and once accepted billed by a deposit invoice
 * when it asks one, then its final invoice.
 * A sent quote can no longer change or be deleted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteService {

    static final String PREFIX = "D";
    static final int DEFAULT_VALIDITY_DAYS = 30;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final QuoteRepository quotes;
    private final QuoteCounterRepository counters;
    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final FreelanceRepository freelances;
    private final InvoiceService invoices;
    private final InvoiceRepository invoiceRepository;
    private final QuotePdfRenderer pdf;
    private final Clock clock;
    private DocumentTermsFileRepository termsFiles;
    private DocumentTemplateService templates;

    /** The Word templates; without them, as in unit tests, Word copies take the built-in layout. */
    @Autowired(required = false)
    void setTemplates(DocumentTemplateService templates) {
        this.templates = templates;
    }

    /** Where the terms and conditions PDFs are read; without it, as in tests, quotes append none. */
    @Autowired(required = false)
    public void setTermsFiles(DocumentTermsFileRepository termsFiles) {
        this.termsFiles = termsFiles;
    }

    @Transactional(readOnly = true)
    public List<QuoteDtos.Quote> list(Long freelanceId) {
        Map<Long, List<Invoice>> billing = invoiceRepository.findByFreelanceIdAndQuoteIdIsNotNull(freelanceId).stream()
            .collect(Collectors.groupingBy(Invoice::getQuoteId));
        return quotes.findByFreelanceIdOrderByCreatedAtDesc(freelanceId).stream()
            .map(quote -> toDto(quote, billing.getOrDefault(quote.getId(), List.of()))).toList();
    }

    @Transactional
    public QuoteDtos.Quote create(Long freelanceId, QuoteDtos.Draft draft) {
        Quote quote = new Quote();
        quote.setFreelanceId(freelanceId);
        write(quote, draft);
        if (quote.getNotes() == null) {
            quote.setNotes(identity(freelanceId).quoteNotes());
        }
        return toDto(quotes.save(quote));
    }

    @Transactional
    public QuoteDtos.Quote update(Long freelanceId, Long id, QuoteDtos.Draft draft) {
        Quote quote = draft(freelanceId, id);
        write(quote, draft);
        return toDto(quote);
    }

    @Transactional
    public void delete(Long freelanceId, Long id) {
        quotes.delete(draft(freelanceId, id));
    }

    /**
     * Sends a draft: the next quote number of the year (the workspace's counter locked meanwhile), the issue date and the
     * day it holds until, the payment term, and the seller and buyer frozen as they are now.
     */
    @Transactional
    public QuoteDtos.Quote send(Long freelanceId, Long id, QuoteDtos.Send request) {
        Quote quote = draft(freelanceId, id);
        BillingIdentityDto seller = identity(freelanceId);
        if (!Boolean.TRUE.equals(seller.complete())) {
            throw new InvalidStateException("Complete the billing identity before sending a quote");
        }
        Client client = clients.findById(quote.getClientId())
            .orElseThrow(() -> new InvalidStateException("The quote's client no longer exists"));
        if (client.getAddress() == null || client.getAddress().isBlank()) {
            throw new InvalidStateException("The client needs an address to receive a quote");
        }
        LocalDate today = today();
        LocalDate issued = request == null || request.issueDate() == null ? today : request.issueDate();
        if (issued.isAfter(today)) {
            throw new IllegalArgumentException("A quote cannot be sent at a later date");
        }
        if (InvoiceTotals.of(quote.getLines()).total().signum() < 0) {
            throw new InvalidStateException("A quote cannot total less than zero");
        }
        quote.setNumber(nextNumber(freelanceId, issued.getYear()));
        quote.setIssueDate(issued);
        quote.setValidUntil(issued.plusDays(quote.getValidityDays()));
        quote.setPaymentTermDays(seller.paymentTermDays());
        quote.setSeller(InvoiceService.seller(seller));
        quote.setBuyer(InvoiceService.buyer(client));
        quote.setTerms(seller.quoteTerms());
        quote.setTermsFileId(seller.quoteTermsFileId());
        quote.setStatus(QuoteStatus.SENT);
        log.info("Workspace {} sent quote {}", freelanceId, quote.getId());
        return toDto(quote);
    }

    /** Records the client's answer to a sent quote, on the day it is given. */
    @Transactional
    public QuoteDtos.Quote decide(Long freelanceId, Long id, QuoteDtos.Decision decision) {
        Quote quote = quote(freelanceId, id);
        if (quote.getStatus() != QuoteStatus.SENT) {
            throw new InvalidStateException("Only a sent quote can be accepted or refused");
        }
        quote.setStatus(Boolean.TRUE.equals(decision.accepted()) ? QuoteStatus.ACCEPTED : QuoteStatus.REFUSED);
        quote.setDecidedOn(today());
        return toDto(quote);
    }

    /**
     * A draft invoice of an accepted quote, once: its lines, client, mission and language, stating the quote's number,
     * less its issued deposit invoice. Deleting the draft lets the quote be invoiced again.
     */
    @Transactional
    public InvoiceDtos.Invoice invoice(Long freelanceId, Long id) {
        Quote quote = billable(freelanceId, id);
        return invoices.fromQuote(freelanceId, quote);
    }

    /** The draft deposit invoice of an accepted quote that asks one, once, before its final invoice. */
    @Transactional
    public InvoiceDtos.Invoice deposit(Long freelanceId, Long id) {
        Quote quote = billable(freelanceId, id);
        if (quote.getDepositPercent() == null) {
            throw new InvalidStateException("The quote asks no deposit");
        }
        if (invoiceRepository.findByQuoteId(quote.getId()).stream().anyMatch(invoice -> invoice.isDeposit() &&
                invoice.getStatus() != InvoiceStatus.CREDITED)) {
            throw new InvalidStateException("The deposit of this quote is already invoiced");
        }
        return invoices.depositFromQuote(freelanceId, quote);
    }

    /** An accepted quote without its final invoice yet. */
    private Quote billable(Long freelanceId, Long id) {
        Quote quote = quote(freelanceId, id);
        if (quote.getStatus() != QuoteStatus.ACCEPTED) {
            throw new InvalidStateException("Only an accepted quote can be invoiced");
        }
        if (invoiceRepository.findByQuoteId(quote.getId()).stream().anyMatch(invoice -> invoice.getKind() == InvoiceKind.INVOICE)) {
            throw new InvalidStateException("This quote is already invoiced");
        }
        return quote;
    }

    /** The quote's PDF: as sent, or, for a draft, with the seller, its payment term and the client as they are now. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile renderPdf(Long freelanceId, Long id) {
        return render(freelanceId, id, false);
    }

    /** The quote as a Word file laid out like its PDF. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile renderDocx(Long freelanceId, Long id) {
        return render(freelanceId, id, true);
    }

    /** The quotes' Word template filled with the latest quote, or with a sample one before the first. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile previewTemplate(Long freelanceId) {
        byte[] template = (templates == null ? java.util.Optional.<byte[]>empty() : templates.content(freelanceId, DocumentTemplateKind.QUOTE))
            .orElseThrow(() -> new dev.swirlit.indezy.exception.ResourceNotFoundException("No Word template for the quotes"));
        java.util.Optional<Quote> latest = quotes.findByFreelanceIdOrderByCreatedAtDesc(freelanceId).stream().findFirst();
        if (latest.isPresent()) {
            return new ActivityReportService.PdfFile("apercu-modele-devis.docx", render(freelanceId, latest.get().getId(), true).content());
        }
        Quote sample = new Quote();
        sample.setLanguage(DocumentLanguage.FR);
        sample.setLines(List.of(new InvoiceLine("Prestation de conseil (exemple)", java.math.BigDecimal.valueOf(5), java.math.BigDecimal.valueOf(600),
            java.math.BigDecimal.valueOf(20))));
        BillingIdentityDto identity = identity(freelanceId);
        return new ActivityReportService.PdfFile("apercu-modele-devis.docx", pdf.renderDocx(sample, InvoiceService.seller(identity),
            InvoiceService.sampleBuyer(), identity.paymentTermDays(), identity.quoteTerms(), identity.quoteTermsFileId() != null, template));
    }

    private ActivityReportService.PdfFile render(Long freelanceId, Long id, boolean word) {
        Quote quote = quote(freelanceId, id);
        boolean draft = quote.getStatus() == QuoteStatus.DRAFT;
        BillingIdentityDto identity = draft ? identity(freelanceId) : null;
        InvoiceSeller seller = draft ? InvoiceService.seller(identity) : quote.getSeller();
        InvoiceBuyer buyer = draft ? clients.findById(quote.getClientId()).map(InvoiceService::buyer).orElseGet(InvoiceBuyer::new) :
            quote.getBuyer();
        Integer paymentTermDays = draft ? identity.paymentTermDays() : quote.getPaymentTermDays();
        boolean english = quote.getLanguage() == DocumentLanguage.EN;
        String fileName = (english ? "quote-" : "devis-") + (draft ? (english ? "draft-" : "brouillon-") + quote.getId() : quote.getNumber()) +
            (word ? ".docx" : ".pdf");
        Long termsFile = draft ? identity.quoteTermsFileId() : quote.getTermsFileId();
        String terms = draft ? identity.quoteTerms() : quote.getTerms();
        if (word) {
            byte[] template = templates == null ? null : templates.content(freelanceId, DocumentTemplateKind.QUOTE).orElse(null);
            return new ActivityReportService.PdfFile(fileName, pdf.renderDocx(quote, seller, buyer, paymentTermDays, terms, termsFile != null,
                template));
        }
        byte[] termsPdf = termsFile == null || termsFiles == null ? null : termsFiles.findById(termsFile).map(DocumentTermsFile::getContent).orElse(null);
        return new ActivityReportService.PdfFile(fileName, pdf.render(quote, seller, buyer, paymentTermDays, terms, termsPdf));
    }

    private String nextNumber(Long freelanceId, int year) {
        QuoteCounter counter = counters.findByFreelanceIdAndYear(freelanceId, year).orElseGet(() -> {
            QuoteCounter created = new QuoteCounter();
            created.setFreelanceId(freelanceId);
            created.setYear(year);
            return counters.saveAndFlush(created);
        });
        counter.setLastNumber(counter.getLastNumber() + 1);
        return String.format("%s%d-%04d", PREFIX, year, counter.getLastNumber());
    }

    private void write(Quote quote, QuoteDtos.Draft draft) {
        Client client = clients.findById(draft.clientId()).filter(found -> found.getFreelance() != null &&
                quote.getFreelanceId().equals(found.getFreelance().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        quote.setClientId(client.getId());
        Project project = draft.projectId() == null ? null : projects.findById(draft.projectId())
            .filter(found -> found.getFreelance() != null && quote.getFreelanceId().equals(found.getFreelance().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        quote.setProjectId(project == null ? null : project.getId());
        quote.setProjectRole(project == null ? null : project.getRole());
        quote.setLanguage(Objects.requireNonNullElse(draft.language(), DocumentLanguage.FR));
        quote.setValidityDays(Objects.requireNonNullElse(draft.validityDays(), DEFAULT_VALIDITY_DAYS));
        quote.setDepositPercent(draft.depositPercent());
        quote.setNotes(draft.notes() == null || draft.notes().isBlank() ? null : draft.notes().strip());
        quote.setLines(InvoiceService.lines(draft.lines()));
    }

    private BillingIdentityDto identity(Long freelanceId) {
        return BillingIdentityService.toDto(freelances.findById(freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found")));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(PARIS));
    }

    private Quote quote(Long freelanceId, Long id) {
        return quotes.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Quote not found"));
    }

    private Quote draft(Long freelanceId, Long id) {
        Quote quote = quote(freelanceId, id);
        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new InvalidStateException("A sent quote can no longer change");
        }
        return quote;
    }

    private QuoteDtos.Quote toDto(Quote quote) {
        return toDto(quote, quote.getId() == null ? List.of() : invoiceRepository.findByQuoteId(quote.getId()));
    }

    /** The quote, with the invoices billing it: its deposit (unless credited) and its final invoice. */
    private QuoteDtos.Quote toDto(Quote quote, List<Invoice> billing) {
        Long invoiceId = billing.stream().filter(invoice -> invoice.getKind() == InvoiceKind.INVOICE).map(Invoice::getId).findFirst().orElse(null);
        Long depositInvoiceId = billing.stream().filter(invoice -> invoice.isDeposit() && invoice.getStatus() != InvoiceStatus.CREDITED)
            .map(Invoice::getId).findFirst().orElse(null);
        InvoiceTotals totals = InvoiceTotals.of(quote.getLines());
        String clientName = quote.getBuyer() != null && quote.getBuyer().getName() != null ? quote.getBuyer().getName() :
            clients.findById(quote.getClientId()).map(Client::getCompanyName).orElse(null);
        boolean expired = quote.getStatus() == QuoteStatus.SENT && quote.getValidUntil() != null && quote.getValidUntil().isBefore(today());
        return new QuoteDtos.Quote(quote.getId(), quote.getNumber(), quote.getStatus(), expired, quote.getClientId(), clientName,
            quote.getProjectId(), quote.getProjectRole(), quote.getIssueDate(), quote.getValidityDays(), quote.getValidUntil(),
            quote.getDepositPercent(), quote.getLanguage(), quote.getNotes(), quote.getLines().stream()
            .map(line -> new InvoiceDtos.Line(line.getDescription(), line.getQuantity(), line.getUnitPrice(), line.getVatRate())).toList(),
            totals.excludingVat(), totals.vat(), totals.total(), quote.getDecidedOn(), invoiceId, depositInvoiceId, quote.getCreatedAt());
    }
}
