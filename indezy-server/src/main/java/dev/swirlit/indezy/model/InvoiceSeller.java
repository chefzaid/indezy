package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.model.enums.LogoPlacement;
import dev.swirlit.indezy.model.enums.LogoSize;
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

    /** The late-payment interest rate stated, in percent a year; null for the statutory rate without a figure. */
    @Column(name = "seller_late_payment_rate")
    private Double latePaymentRate;

    /** A line under the seller's name (a tagline, contact details), kept at issue; null for none. */
    @Column(name = "seller_header", length = 150)
    private String header;

    /** The free text at the foot of each page, kept at issue; null for none. */
    @Column(name = "seller_footer", length = 200)
    private String footer;

    /** Where the logo sits and how large, kept at issue; null for left and medium. */
    /** The font of the document; null for the sans-serif one. */
    @Enumerated(EnumType.STRING)
    @Column(name = "seller_font", length = 10)
    private DocumentFont font;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_logo_placement", length = 10)
    private LogoPlacement logoPlacement;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_logo_size", length = 10)
    private LogoSize logoSize;

    /** The uploaded font ({@link DocumentFontFile}) the document prints in when {@code font} is CUSTOM, kept at issue. */
    @Column(name = "seller_font_file_id")
    private Long fontFileId;

    @Column(name = "seller_font_family", length = 100)
    private String fontFamily;

    /** The logo the document prints ({@link DocumentLogo}), kept at issue; null for none. */
    @Column(name = "seller_logo_id")
    private Long logoId;

    /** The accent colour of the document's title, table header and rules, as #RRGGBB; null for black. */
    @Column(name = "seller_accent_color", length = 7)
    private String accentColor;
}
