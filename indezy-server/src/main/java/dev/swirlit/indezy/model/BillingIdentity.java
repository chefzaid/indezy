package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.model.enums.DocumentTheme;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.model.enums.LogoPlacement;
import dev.swirlit.indezy.model.enums.LogoSize;
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

    /**
     * The late-payment interest rate the freelancer's terms state, in percent a year; null states the statutory rate
     * (the ECB's refinancing rate plus 10 points) without a figure.
     */
    /** The accent colour of the documents' title, table header and rules, as #RRGGBB; null for black. */
    @Column(name = "billing_accent_color", length = 7)
    private String accentColor;

    /** The notes a new invoice starts with; null for none. */
    @Column(name = "billing_invoice_notes", length = 2000)
    private String invoiceNotes;

    /** The notes a new quote starts with; null for none. */
    @Column(name = "billing_quote_notes", length = 2000)
    private String quoteNotes;

    /** A line under the seller's name on the documents (a tagline, contact details); null for none. */
    @Column(name = "billing_header", length = 150)
    private String header;

    /** The free text at the foot of each page of the documents (website, phone); null for none. */
    @Column(name = "billing_footer", length = 200)
    private String footer;

    /** The font of the document; null for the sans-serif one. */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_font", length = 10)
    private DocumentFont font;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_logo_placement", length = 10)
    private LogoPlacement logoPlacement;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_logo_size", length = 10)
    private LogoSize logoSize;

    /** The logo printed on the documents ({@link DocumentLogo}); null for none. */
    @Column(name = "billing_logo_id")
    private Long logoId;

    /** The layout of the documents; null for the simple one. */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_theme", length = 10)
    private DocumentTheme theme;

    /** The letterhead PDF the documents are drawn over ({@link DocumentLetterhead}); null for none. */
    @Column(name = "billing_letterhead_id")
    private Long letterheadId;

    /** The uploaded font ({@link DocumentFontFile}) the documents print in when {@code font} is CUSTOM; null for none. */
    @Column(name = "billing_font_file_id")
    private Long fontFileId;

    /** That font's family name. */
    @Column(name = "billing_font_family", length = 100)
    private String fontFamily;

    /** The terms and conditions of sale as a PDF appended after each quote ({@link DocumentTermsFile}); null for none. */
    @Column(name = "billing_quote_terms_file_id")
    private Long quoteTermsFileId;

    /** The terms and conditions of sale printed after each quote; null for none. */
    @Column(name = "billing_quote_terms", columnDefinition = "TEXT")
    private String quoteTerms;

    @Column(name = "billing_late_payment_rate")
    private Double latePaymentRate;
}
