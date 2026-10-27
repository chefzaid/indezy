package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingTextTest {

    @Test
    void compactsIdentifiersAsPeopleTypeThem() {
        assertThat(BillingText.compact(" 123 456 789 ", false)).isEqualTo("123456789");
        assertThat(BillingText.compact("fr 40 303265045", true)).isEqualTo("FR40303265045");
        assertThat(BillingText.compact("   ", true)).isNull();
        assertThat(BillingText.compact(null, true)).isNull();
    }

    @Test
    void checksAnIbansDigits() {
        assertThat(BillingText.isIban("FR7630006000011234567890189")).isTrue();
        assertThat(BillingText.isIban("DE89370400440532013000")).isTrue();
        assertThat(BillingText.isIban("FR7630006000011234567890188")).isFalse();
        assertThat(BillingText.isIban("FR76")).isFalse();
        assertThat(BillingText.isIban(null)).isFalse();
        assertThat(BillingText.groupIban("FR7630006000011234567890189")).isEqualTo("FR76 3000 6000 0112 3456 7890 189");
    }
}
