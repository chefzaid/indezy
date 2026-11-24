package dev.swirlit.indezy.model.enums;

/**
 * How a freelancer charging VAT declares it: the simplified regime (two instalments and an annual return), or the
 * normal one, with a return (CA3) every month, or every quarter when the year's VAT stays under 4,000 €.
 */
public enum VatRegime {
    SIMPLIFIED,
    NORMAL_MONTHLY,
    NORMAL_QUARTERLY
}
