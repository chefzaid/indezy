package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.LegalForm;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** What a freelancer's invoices say of the seller: legal name and form, registration, VAT, address and bank details. */
@Embeddable
@Getter
@Setter
public class BillingIdentity {

    @Column(name = "billing_legal_name", length = 120)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_legal_form", length = 20)
    private LegalForm legalForm;

    @Column(name = "billing_siret", length = 14)
    private String siret;

    @Column(name = "billing_vat_number", length = 20)
    private String vatNumber;

    /** Exempt from VAT (franchise en base, art. 293 B du CGI); a null column reads as exempt. */
    @Column(name = "billing_vat_exempt")
    private Boolean vatExempt;

    /** The VAT rate in percent, when not exempt. */
    @Column(name = "billing_vat_rate")
    private Double vatRate;

    @Column(name = "billing_share_capital")
    private Integer shareCapital;

    /** The registration, as invoices write it: "RCS Paris 123 456 789". */
    @Column(name = "billing_registration", length = 120)
    private String registration;

    @Column(name = "billing_address", length = 300)
    private String address;

    @Column(name = "billing_iban", length = 34)
    private String iban;

    @Column(name = "billing_bic", length = 11)
    private String bic;

    @Column(name = "billing_payment_term_days")
    private Integer paymentTermDays;

    @Column(name = "billing_invoice_prefix", length = 10)
    private String invoicePrefix;
}
