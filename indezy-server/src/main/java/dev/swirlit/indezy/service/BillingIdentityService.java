package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.model.enums.DocumentTheme;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.model.enums.LogoPlacement;
import dev.swirlit.indezy.model.enums.LogoSize;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.util.BillingText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return billing;
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
            billing.getLogoId(), billing.getFooter(), billing.getInvoiceNotes(), billing.getQuoteNotes(),
            billing.getQuoteTermsFileId(), Objects.requireNonNullElse(billing.getLogoPlacement(), LogoPlacement.LEFT),
            Objects.requireNonNullElse(billing.getLogoSize(), LogoSize.MEDIUM), billing.getHeader(),
            Objects.requireNonNullElse(billing.getFont(), DocumentFont.SANS), Objects.requireNonNullElse(billing.getTheme(), DocumentTheme.SIMPLE),
            billing.getFontFileId(), billing.getFontFamily(),
            billing.getLetterheadId(), billing.getLetterheadTopMm(), billing.getLetterheadBottomMm(), complete);
    }

    private Freelance freelance(Long freelanceId) {
        return freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }
}
