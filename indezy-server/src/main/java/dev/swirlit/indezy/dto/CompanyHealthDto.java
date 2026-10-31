package dev.swirlit.indezy.dto;

import java.time.LocalDate;

/**
 * Whether a company is still in business (see docs/features.md#company-health-check): closed according to the public
 * directory, or under insolvency proceedings according to its latest BODACC announcement of the last three years.
 * {@code checked} is false when it has no SIREN or neither source answered.
 */
public record CompanyHealthDto(boolean checked, boolean closed, Procedure procedure, LocalDate procedureOn) {

    public static final CompanyHealthDto UNCHECKED = new CompanyHealthDto(false, false, null, null);

    /** The kind of collective proceedings an announcement reports. */
    public enum Procedure {
        SAUVEGARDE,
        REDRESSEMENT,
        LIQUIDATION,
        OTHER
    }
}
