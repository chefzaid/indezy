package dev.swirlit.indezy.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KilometreScaleTest {

    @Test
    void appliesTheBandOfTheYearsDistanceAndTwentyPercentMoreForAnElectricCar() {
        // 4 000 km in a 5 CV: d x 0,636.
        assertThat(KilometreScale.amount(5, new BigDecimal("4000"), false)).isEqualByComparingTo("2544.00");
        // 12 000 km in a 4 CV: (d x 0,340) + 1 330.
        assertThat(KilometreScale.amount(4, new BigDecimal("12000"), false)).isEqualByComparingTo("5410.00");
        // 25 000 km in a 7 CV or more: d x 0,470.
        assertThat(KilometreScale.amount(9, new BigDecimal("25000"), false)).isEqualByComparingTo("11750.00");
        // 3 CV or less, electric: 1 000 x 0,529 x 1,2.
        assertThat(KilometreScale.amount(2, new BigDecimal("1000"), true)).isEqualByComparingTo("634.80");
        // Either side of 5 000 km in a 6 CV.
        assertThat(KilometreScale.amount(6, new BigDecimal("5000"), false)).isEqualByComparingTo("3325.00");
        assertThat(KilometreScale.amount(6, new BigDecimal("5001"), false)).isEqualByComparingTo("3327.37");
    }
}
