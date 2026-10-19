package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTextTest {

    @Test
    void keepsParagraphsLineBreaksAndListItems() {
        String html = "<h2>Contexte</h2><p>Une   mission&nbsp;longue<br>à Paris</p>" +
            "<ul><li>Java</li><li>Kafka &amp; Spark</li></ul><div>Fin</div>";

        assertThat(HtmlText.toText(html)).isEqualTo("Contexte\n\nUne mission longue\nà Paris\n\n• Java\n• Kafka & Spark\n\nFin");
    }

    @Test
    void dropsScriptsAndMarkup() {
        String html = "<p onclick=\"steal()\">Bonjour<script>alert('x')</script> <img src=x onerror=alert(1)><b>Zoé</b></p>";

        assertThat(HtmlText.toText(html)).isEqualTo("Bonjour Zoé");
    }

    @Test
    void returnsNothingForEmptyHtml() {
        assertThat(HtmlText.toText(null)).isNull();
        assertThat(HtmlText.toText("   ")).isNull();
        assertThat(HtmlText.toText("<p> </p><br>")).isNull();
    }

    @Test
    void capsVeryLongDescriptions() {
        String text = HtmlText.toText("<p>" + "a".repeat(HtmlText.MAX_LENGTH + 500) + "</p>");

        assertThat(text).hasSize(HtmlText.MAX_LENGTH);
    }
}
