package dev.swirlit.indezy.dto;

/**
 * A company of the French public directory (see docs/features.md#company-lookup): its SIREN, legal name, head office,
 * main activity (NAF code), legal form, intra-EU VAT number computed from the SIREN, and whether it has closed.
 */
public record CompanyDto(String siren, String name, String address, String postalCode, String city, String nafCode,
                         String legalForm, String vatNumber, boolean closed) {
}
