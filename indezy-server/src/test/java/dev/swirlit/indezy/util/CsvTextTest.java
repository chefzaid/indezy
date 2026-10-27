package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTextTest {

    @Test
    void quotesFieldsHoldingTheSeparatorAQuoteOrALineBreak() {
        assertThat(CsvText.row(";", "plain", "a;b", "say \"hi\"", "two\nlines", "back\rhere", null, "a,b"))
            .isEqualTo("plain;\"a;b\";\"say \"\"hi\"\"\";\"two\nlines\";\"back\rhere\";;a,b");
        assertThat(CsvText.row(",", "a,b")).isEqualTo("\"a,b\"");
    }

    @Test
    void keepsTextThatASpreadsheetWouldReadAsAFormula() {
        assertThat(CsvText.text("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvText.text("+33 6")).isEqualTo("'+33 6");
        assertThat(CsvText.text("-2")).isEqualTo("'-2");
        assertThat(CsvText.text("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvText.text("\tcmd")).isEqualTo("'\tcmd");
        assertThat(CsvText.text("\rcmd")).isEqualTo("'\rcmd");
        assertThat(CsvText.text("Acme")).isEqualTo("Acme");
        assertThat(CsvText.text("")).isEmpty();
        assertThat(CsvText.text(null)).isEmpty();
    }
}
