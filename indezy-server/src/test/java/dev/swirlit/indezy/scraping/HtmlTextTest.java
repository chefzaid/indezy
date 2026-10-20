package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTextTest {

    @Test
    void joinsTheTextsThatAreNotBlank() {
        assertThat(HtmlText.join(" / ", "Paris", null, " ", "Lyon")).isEqualTo("Paris / Lyon");
        assertThat(HtmlText.join(" / ", null, "")).isNull();
    }

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
    void readsHtmlASiteEscapedTwiceAndKeepsAPlainLessThan() {
        String html = "<p>mission<br>Le consultant :&lt;br /&gt;&lt;br /&gt;Construction.&lt;br /&gt;Mise en place.</p>";

        assertThat(HtmlText.toText(html)).isEqualTo("mission\nLe consultant :\n\nConstruction.\nMise en place.");
        assertThat(HtmlText.toText("<p>Java &lt; 5 ans &amp; C++ &gt; 3 ans</p>")).isEqualTo("Java < 5 ans & C++ > 3 ans");
        assertThat(HtmlText.hasEscapedMarkup("Java < 5 ans > 3")).isFalse();
        assertThat(HtmlText.hasEscapedMarkup("a<BR/>b")).isTrue();
        assertThat(HtmlText.hasEscapedMarkup(null)).isFalse();
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
