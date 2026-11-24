package dev.swirlit.indezy.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The tax administration's kilometre scale for cars (barème kilométrique, BOI-BAREME-000001; see
 * docs/features.md#professional-kilometres): for the year's professional distance d, a rate per kilometre up to 5 000 km,
 * a rate plus a fixed amount from 5 001 to 20 000 km, a rate beyond; 20 % more for an electric car. The scale of the
 * 2022 incomes, still the one impots.gouv.fr's simulator applies (checked 2026-11-24).
 */
public final class KilometreScale {

    /** The lowest and highest fiscal horsepower of the scale: 3 CV and less, 7 CV and more. */
    public static final int MIN_HORSEPOWER = 3;
    public static final int MAX_HORSEPOWER = 7;

    private static final BigDecimal FIRST_BAND = BigDecimal.valueOf(5_000);
    private static final BigDecimal SECOND_BAND = BigDecimal.valueOf(20_000);
    private static final BigDecimal ELECTRIC = new BigDecimal("1.20");

    /** Per horsepower, from 3 CV: the rate up to 5 000 km, the rate and the fixed amount up to 20 000 km, the rate beyond. */
    private static final String[][] RATES = {
        {"0.529", "0.316", "1065", "0.370"},
        {"0.606", "0.340", "1330", "0.407"},
        {"0.636", "0.357", "1395", "0.427"},
        {"0.665", "0.374", "1457", "0.447"},
        {"0.697", "0.394", "1515", "0.470"},
    };

    private KilometreScale() {
    }

    /** The amount, in euros to the cent, for {@code kilometres} driven in the year with a car of {@code horsepower} CV. */
    public static BigDecimal amount(int horsepower, BigDecimal kilometres, boolean electric) {
        int row = Math.max(MIN_HORSEPOWER, Math.min(MAX_HORSEPOWER, horsepower)) - MIN_HORSEPOWER;
        String[] rates = RATES[row];
        BigDecimal amount;
        if (kilometres.compareTo(FIRST_BAND) <= 0) {
            amount = kilometres.multiply(new BigDecimal(rates[0]));
        } else if (kilometres.compareTo(SECOND_BAND) <= 0) {
            amount = kilometres.multiply(new BigDecimal(rates[1])).add(new BigDecimal(rates[2]));
        } else {
            amount = kilometres.multiply(new BigDecimal(rates[3]));
        }
        return (electric ? amount.multiply(ELECTRIC) : amount).setScale(2, RoundingMode.HALF_UP);
    }
}
