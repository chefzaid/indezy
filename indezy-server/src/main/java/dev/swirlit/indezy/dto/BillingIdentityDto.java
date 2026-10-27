package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.LegalForm;
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
    Boolean complete) {

    public BillingIdentityDto {
        siret = BillingText.compact(siret, false);
        vatNumber = BillingText.compact(vatNumber, true);
        iban = BillingText.compact(iban, true);
        bic = BillingText.compact(bic, true);
        legalName = blankToNull(legalName);
        registration = blankToNull(registration);
        address = blankToNull(address);
        invoicePrefix = blankToNull(invoicePrefix);
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
