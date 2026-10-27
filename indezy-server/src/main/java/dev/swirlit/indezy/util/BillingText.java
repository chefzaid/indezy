package dev.swirlit.indezy.util;

import java.math.BigInteger;
import java.util.Locale;

/** Billing identifiers as people type them (in groups, lower case) made comparable, and IBANs checked. */
public final class BillingText {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private BillingText() {
    }

    /** The value without spaces (and upper case when asked); null when blank. */
    public static String compact(String value, boolean upperCase) {
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            return null;
        }
        return upperCase ? compact.toUpperCase(Locale.ROOT) : compact;
    }

    /** Whether an IBAN (already compact and upper case) has a valid shape and ISO 13616 check digits. */
    public static boolean isIban(String iban) {
        if (iban == null || !iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}")) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    /** The IBAN in groups of four, as invoices print it. */
    public static String groupIban(String iban) {
        return iban == null ? null : iban.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
