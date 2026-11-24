package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.model.enums.BodyBlock;
import dev.swirlit.indezy.model.enums.DocumentTheme;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DocumentTemplateKind;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.model.enums.QuoteStatus;
import dev.swirlit.indezy.pdf.InvoicePdfRenderer;
import dev.swirlit.indezy.pdf.QuotePdfRenderer;
import dev.swirlit.indezy.pdf.StarterTemplate;
import dev.swirlit.indezy.model.enums.DeclarationFrequency;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.model.enums.MicroActivity;
import dev.swirlit.indezy.model.enums.LogoPlacement;
import dev.swirlit.indezy.model.enums.LogoSize;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.util.BillingText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.time.Clock;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The seller's identity on a workspace's invoices, with defaults drawn from the profile until it is set. */
@Service
@RequiredArgsConstructor
public class BillingIdentityService {

    static final double DEFAULT_VAT_RATE = 20.0;
    static final int DEFAULT_PAYMENT_TERM_DAYS = 30;
    static final String DEFAULT_PREFIX = "F";

    private final FreelanceRepository freelances;
    private final InvoicePdfRenderer invoicePdf;
    private final QuotePdfRenderer quotePdf;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BillingIdentityDto get(Long freelanceId) {
        return toDto(freelance(freelanceId));
    }

    @Transactional
    public BillingIdentityDto update(Long freelanceId, BillingIdentityDto request) {
        if (request.iban() != null && !BillingText.isIban(request.iban())) {
            throw new FieldRejectedException("iban", "This IBAN is not valid");
        }
        Freelance freelance = freelance(freelanceId);
        freelance.setBilling(apply(freelance.getBilling(), request));
        return toDto(freelance);
    }

    /**
     * The identity the form would save, not saved: for the style's preview, its files (logo, font, letterhead) as they
     * are, the form setting the rest.
     */
    @Transactional(readOnly = true)
    public BillingIdentityDto previewed(Long freelanceId, BillingIdentityDto request) {
        Freelance freelance = freelance(freelanceId);
        return toDto(freelance, apply(freelance.getBilling(), request));
    }

    /** The form's values over the stored identity, whose files only its own uploads set. */
    private static BillingIdentity apply(BillingIdentity stored, BillingIdentityDto request) {
        Freelance freelance = new Freelance();
        freelance.setBilling(stored);
        BillingIdentity billing = new BillingIdentity();
        // The logo is set on its own (DocumentLogoService), never by the identity's form.
        billing.setLogoId(freelance.getBilling() == null ? null : freelance.getBilling().getLogoId());
        billing.setQuoteTermsFileId(freelance.getBilling() == null ? null : freelance.getBilling().getQuoteTermsFileId());
        billing.setFontFileId(freelance.getBilling() == null ? null : freelance.getBilling().getFontFileId());
        billing.setLetterheadId(freelance.getBilling() == null ? null : freelance.getBilling().getLetterheadId());
        billing.setFontFamily(freelance.getBilling() == null ? null : freelance.getBilling().getFontFamily());
        billing.setLegalName(request.legalName());
        billing.setLegalForm(request.legalForm());
        billing.setSiret(request.siret());
        billing.setVatNumber(request.vatNumber());
        billing.setVatExempt(!Boolean.FALSE.equals(request.vatExempt()));
        billing.setVatRate(request.vatRate());
        billing.setShareCapital(request.shareCapital());
        billing.setRegistration(request.registration());
        billing.setAddress(request.address());
        billing.setIban(request.iban());
        billing.setBic(request.bic());
        billing.setPaymentTermDays(request.paymentTermDays());
        billing.setInvoicePrefix(request.invoicePrefix());
        billing.setLatePaymentRate(request.latePaymentRate());
        billing.setQuoteTerms(request.quoteTerms());
        billing.setAccentColor(request.accentColor());
        billing.setFooter(request.footer());
        billing.setFooterCenter(request.footerCenter());
        billing.setFooterRight(request.footerRight());
        billing.setPageHeaderLeft(request.pageHeaderLeft());
        billing.setPageHeaderRight(request.pageHeaderRight());
        billing.setInvoiceNotes(request.invoiceNotes());
        billing.setLogoPlacement(request.logoPlacement());
        billing.setLogoSize(request.logoSize());
        billing.setHeader(request.header());
        // The uploaded font is chosen only while there is one.
        billing.setFont(request.font() == DocumentFont.CUSTOM && billing.getFontFileId() == null ? DocumentFont.SANS : request.font());
        billing.setQuoteNotes(request.quoteNotes());
        billing.setTheme(request.theme());
        billing.setLetterheadTopMm(request.letterheadTop());
        billing.setLetterheadBottomMm(request.letterheadBottom());
        billing.setQuoteAccentColor(request.quoteAccentColor());
        billing.setQuoteTheme(request.quoteTheme());
        billing.setBodyBlocks(BodyBlock.store(request.bodyBlocks()));
        billing.setBlockStyles(dev.swirlit.indezy.model.BlockStyle.store(request.blockStyles()));
        billing.setMicroActivity(request.microActivity());
        billing.setIncomeTaxDischarge(request.incomeTaxDischarge());
        billing.setDeclarationFrequency(request.declarationFrequency());
        billing.setOwnContributionRate(request.ownContributionRate());
        billing.setIncomeTaxFrequency(request.incomeTaxFrequency());
        return billing;
    }

    /** A Word template to start from, in the style the documents of that kind take today (see {@link dev.swirlit.indezy.pdf.StarterTemplate}). */
    @Transactional(readOnly = true)
    public byte[] starterTemplate(Long freelanceId, DocumentTemplateKind kind, DocumentLanguage language) {
        return starterTemplate(freelanceId, kind, language, null);
    }

    /** The starter template in one of the built-in layouts; null for the documents' own. */
    @Transactional(readOnly = true)
    public byte[] starterTemplate(Long freelanceId, DocumentTemplateKind kind, DocumentLanguage language, DocumentTheme theme) {
        return StarterTemplate.write(kind, language, sellerOf(freelanceId, kind), theme);
    }

    /**
     * The starter template filled with a sample document numbered and dated today, in the language given: what the
     * template becomes once uploaded, to see before editing it.
     */
    @Transactional(readOnly = true)
    public byte[] starterExample(Long freelanceId, DocumentTemplateKind kind, DocumentLanguage language, DocumentTheme theme) {
        InvoiceSeller seller = sellerOf(freelanceId, kind);
        byte[] starter = StarterTemplate.write(kind, language, seller, theme);
        boolean english = language == DocumentLanguage.EN;
        List<InvoiceLine> lines = List.of(new InvoiceLine(english ? "Consulting (example)" : "Prestation de conseil (exemple)",
            BigDecimal.valueOf(5), BigDecimal.valueOf(600), BigDecimal.valueOf(20)));
        LocalDate today = LocalDate.now(clock);
        if (kind == DocumentTemplateKind.QUOTE) {
            Quote quote = new Quote();
            quote.setLanguage(language);
            quote.setStatus(QuoteStatus.SENT);
            quote.setNumber("D" + today.getYear() + "-0001");
            quote.setIssueDate(today);
            quote.setLines(lines);
            BillingIdentityDto identity = toDto(freelance(freelanceId));
            return quotePdf.renderDocx(quote, seller, InvoiceService.sampleBuyer(), identity.paymentTermDays(), null, false, starter);
        }
        Invoice invoice = new Invoice();
        invoice.setLanguage(language);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setNumber(DEFAULT_PREFIX + today.getYear() + "-0001");
        invoice.setIssueDate(today);
        invoice.setDueDate(today.plusDays(DEFAULT_PAYMENT_TERM_DAYS));
        invoice.setLines(lines);
        return invoicePdf.renderDocx(invoice, seller, InvoiceService.sampleBuyer(), starter);
    }

    private InvoiceSeller sellerOf(Long freelanceId, DocumentTemplateKind kind) {
        BillingIdentityDto identity = toDto(freelance(freelanceId));
        return kind == DocumentTemplateKind.QUOTE ? InvoiceService.quoteSeller(identity) : InvoiceService.seller(identity);
    }

    /** The identity as invoices use it, the defaults filled in. */
    public static BillingIdentityDto toDto(Freelance freelance) {
        return toDto(freelance, freelance.getBilling());
    }

    private static BillingIdentityDto toDto(Freelance freelance, BillingIdentity stored) {
        BillingIdentity billing = Objects.requireNonNullElseGet(stored, BillingIdentity::new);
        String legalName = Objects.requireNonNullElse(billing.getLegalName(), freelance.getFullName());
        String address = billing.getAddress() != null ? billing.getAddress() : Stream.of(freelance.getAddress(), freelance.getCity())
            .filter(part -> part != null && !part.isBlank()).collect(Collectors.collectingAndThen(Collectors.joining("\n"),
                joined -> joined.isEmpty() ? null : joined));
        boolean exempt = !Boolean.FALSE.equals(billing.getVatExempt());
        boolean complete = billing.getSiret() != null && address != null && billing.getIban() != null &&
            (exempt || billing.getVatNumber() != null) && billing.getLegalForm() != LegalForm.PORTAGE;
        return new BillingIdentityDto(legalName, billing.getLegalForm(), billing.getSiret(), billing.getVatNumber(), exempt,
            Objects.requireNonNullElse(billing.getVatRate(), DEFAULT_VAT_RATE), billing.getShareCapital(), billing.getRegistration(),
            address, billing.getIban(), billing.getBic(), Objects.requireNonNullElse(billing.getPaymentTermDays(), DEFAULT_PAYMENT_TERM_DAYS),
            Objects.requireNonNullElse(billing.getInvoicePrefix(), DEFAULT_PREFIX), billing.getLatePaymentRate(), billing.getQuoteTerms(), billing.getAccentColor(),
            billing.getLogoId(), billing.getFooter(), billing.getFooterCenter(), billing.getFooterRight(), billing.getPageHeaderLeft(),
            billing.getPageHeaderRight(), billing.getInvoiceNotes(), billing.getQuoteNotes(),
            billing.getQuoteTermsFileId(), Objects.requireNonNullElse(billing.getLogoPlacement(), LogoPlacement.LEFT),
            Objects.requireNonNullElse(billing.getLogoSize(), LogoSize.MEDIUM), billing.getHeader(),
            Objects.requireNonNullElse(billing.getFont(), DocumentFont.SANS), Objects.requireNonNullElse(billing.getTheme(), DocumentTheme.SIMPLE),
            billing.getFontFileId(), billing.getFontFamily(),
            billing.getLetterheadId(), billing.getLetterheadTopMm(), billing.getLetterheadBottomMm(),
            billing.getQuoteAccentColor(), billing.getQuoteTheme(), BodyBlock.read(billing.getBodyBlocks()),
            dev.swirlit.indezy.model.BlockStyle.read(billing.getBlockStyles()),
            Objects.requireNonNullElse(billing.getMicroActivity(), MicroActivity.BNC_SSI), Boolean.TRUE.equals(billing.getIncomeTaxDischarge()),
            Objects.requireNonNullElse(billing.getDeclarationFrequency(), DeclarationFrequency.MONTHLY), billing.getOwnContributionRate(),
            Objects.requireNonNullElse(billing.getIncomeTaxFrequency(), DeclarationFrequency.MONTHLY), complete);
    }

    private Freelance freelance(Long freelanceId) {
        return freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }
}
