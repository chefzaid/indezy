package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.LegalForm;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** The seller as the invoice stated it when issued: later changes to the billing identity leave it as it was. */
@Embeddable
@Getter
@Setter
public class InvoiceSeller {

    @Column(name = "seller_name", length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_legal_form", length = 20)
    private LegalForm legalForm;

    @Column(name = "seller_siret", length = 14)
    private String siret;

    @Column(name = "seller_vat_number", length = 20)
    private String vatNumber;

    @Column(name = "seller_vat_exempt")
    private Boolean vatExempt;

    @Column(name = "seller_share_capital")
    private Integer shareCapital;

    @Column(name = "seller_registration", length = 120)
    private String registration;

    @Column(name = "seller_address", length = 300)
    private String address;

    @Column(name = "seller_iban", length = 34)
    private String iban;

    @Column(name = "seller_bic", length = 11)
    private String bic;
}
