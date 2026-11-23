package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.model.enums.BodyBlock;
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
import java.util.List;

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
    /** The footer's centre and right zones and the header of the pages after the first, with placeholders; null for none. */
    @Size(max = 200) String footerCenter,
    @Size(max = 200) String footerRight,
    @Size(max = 200) String pageHeaderLeft,
    @Size(max = 200) String pageHeaderRight,
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
    /** The room the letterhead's header and footer take, in millimetres; null for the usual margins. */
    @Min(0) @Max(120) Integer letterheadTop,
    @Min(0) @Max(120) Integer letterheadBottom,
    /** The quotes' own accent colour and layout; null for the invoices' ones. */
    @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "A colour as #RRGGBB") String quoteAccentColor,
    DocumentTheme quoteTheme,
    /** The order of the body's blocks; the lines, mentions and notes always print, the signature box (quotes) only when listed. */
    List<BodyBlock> bodyBlocks,
    /** The mentions' and the notes' own styles (size, alignment, frame, shading); the others keep the layout's. */
    java.util.Map<BodyBlock, dev.swirlit.indezy.model.BlockStyle> blockStyles,
    /** For the URSSAF contributions (docs/features.md#urssaf-contributions): a micro-entrepreneur's activity, the versement
     * libératoire, how often the revenue is declared, and the member's own rate in percent of the revenue (null for the official one). */
    dev.swirlit.indezy.model.enums.MicroActivity microActivity,
    Boolean incomeTaxDischarge,
    dev.swirlit.indezy.model.enums.DeclarationFrequency declarationFrequency,
    @DecimalMin("0") @DecimalMax("100") Double ownContributionRate,
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
        footerCenter = blankToNull(footerCenter);
        footerRight = blankToNull(footerRight);
        pageHeaderLeft = blankToNull(pageHeaderLeft);
        pageHeaderRight = blankToNull(pageHeaderRight);
        header = header == null || header.isBlank() ? null : header.strip().replaceAll("\\s+", " ");
        invoiceNotes = blankToNull(invoiceNotes);
        quoteNotes = blankToNull(quoteNotes);
        accentColor = accentColor == null || accentColor.isBlank() ? null : accentColor.strip().toUpperCase(java.util.Locale.ROOT);
        quoteAccentColor = quoteAccentColor == null || quoteAccentColor.isBlank() ? null : quoteAccentColor.strip().toUpperCase(java.util.Locale.ROOT);
        bodyBlocks = List.copyOf(BodyBlock.normalize(bodyBlocks));
        blockStyles = java.util.Map.copyOf(dev.swirlit.indezy.model.BlockStyle.normalize(blockStyles));
    }

    /** Whether the colour stands out enough from white paper to read the title and print the rules: a contrast of 3 to 1 at least. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "This colour is too pale to read or print: pick a darker one")
    public boolean isAccentReadable() {
        return accentColor == null || !accentColor.matches("#[0-9A-F]{6}") || BillingText.contrastOnWhite(accentColor) >= 3.0;
    }

    /** The same check for the quotes' own colour. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "This colour is too pale to read or print: pick a darker one")
    public boolean isQuoteAccentReadable() {
        return quoteAccentColor == null || !quoteAccentColor.matches("#[0-9A-F]{6}") || BillingText.contrastOnWhite(quoteAccentColor) >= 3.0;
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
