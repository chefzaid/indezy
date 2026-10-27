package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.LegalForm;
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
        BillingIdentity billing = new BillingIdentity();
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
        freelance.setBilling(billing);
        return toDto(freelance);
    }

    /** The identity as invoices use it, the defaults filled in. */
    public static BillingIdentityDto toDto(Freelance freelance) {
        BillingIdentity billing = Objects.requireNonNullElseGet(freelance.getBilling(), BillingIdentity::new);
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
            Objects.requireNonNullElse(billing.getInvoicePrefix(), DEFAULT_PREFIX), complete);
    }

    private Freelance freelance(Long freelanceId) {
        return freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }
}
