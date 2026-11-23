package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.model.enums.DocumentTheme;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.model.enums.LogoPlacement;
import dev.swirlit.indezy.model.enums.LogoSize;
import dev.swirlit.indezy.util.BillingText;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the workspace's invoices say of the seller. Identifiers arrive as people type them and are kept compact. {@code
 * complete} (read-only) says whether invoices can be issued: a legal name, a SIRET, an address, an IBAN, and a VAT number
 * unless exempt.
 */
public record BillingIdentityDto(
    @Size(max = 120) String legalName,
    LegalForm legalForm,
    @Pattern(regexp = "\\d{14}", message = "A SIRET has 14 digits") String siret,
    @Pattern(regexp = "[A-Z]{2}[0-9A-Z]{2,13}", message = "A VAT number starts with its country code") String vatNumber,
    Boolean vatExempt,
    @DecimalMin("0") @DecimalMax("30") Double vatRate,
    @Min(0) Integer shareCapital,
    @Size(max = 120) String registration,
    @Size(max = 300) String address,
    @Size(max = 34) String iban,
    @Pattern(regexp = "[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?", message = "A BIC has 8 or 11 letters and digits") String bic,
    @Min(0) @Max(60) Integer paymentTermDays,
    @Pattern(regexp = "[A-Za-z0-9-]{0,10}", message = "Letters, digits and dashes, 10 at most") String invoicePrefix,
    @DecimalMin("0") @DecimalMax("100") Double latePaymentRate,
    @Size(max = BillingIdentityDto.QUOTE_TERMS_MAX) String quoteTerms,
    @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "A colour as #RRGGBB") String accentColor,
    Long logoId,
    @Size(max = 200) String footer,
    @Size(max = 2000) String invoiceNotes,
    @Size(max = 2000) String quoteNotes,
    Long quoteTermsFileId,
    LogoPlacement logoPlacement,
    LogoSize logoSize,
    @Size(max = 150) String header,
    DocumentFont font,
    /** The layout of the documents: simple, modern or classic. */
    DocumentTheme theme,
    /** The uploaded font and its family, set by its own upload, never by this form. */
    Long fontFileId,
    String fontFamily,
    /** The letterhead PDF the documents are drawn over, set by its own upload, never by this form. */
    Long letterheadId,
    Boolean complete) {

    public static final int QUOTE_TERMS_MAX = 20_000;

    public BillingIdentityDto {
        siret = BillingText.compact(siret, false);
        vatNumber = BillingText.compact(vatNumber, true);
        iban = BillingText.compact(iban, true);
        bic = BillingText.compact(bic, true);
        legalName = blankToNull(legalName);
        registration = blankToNull(registration);
        address = blankToNull(address);
        invoicePrefix = blankToNull(invoicePrefix);
        quoteTerms = blankToNull(quoteTerms);
        footer = blankToNull(footer);
        header = header == null || header.isBlank() ? null : header.strip().replaceAll("\\s+", " ");
        invoiceNotes = blankToNull(invoiceNotes);
        quoteNotes = blankToNull(quoteNotes);
        accentColor = accentColor == null || accentColor.isBlank() ? null : accentColor.strip().toUpperCase(java.util.Locale.ROOT);
    }

    /** Whether the colour stands out enough from white paper to read the title and print the rules: a contrast of 3 to 1 at least. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "This colour is too pale to read or print: pick a darker one")
    public boolean isAccentReadable() {
        return accentColor == null || !accentColor.matches("#[0-9A-F]{6}") || BillingText.contrastOnWhite(accentColor) >= 3.0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString() {
        // The IBAN and the identifiers are kept out of logs.
        return "BillingIdentityDto[" + legalForm + ", complete=" + complete + "]";
    }
}
