package dev.swirlit.indezy.scraping;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PageFactsTest {

    @Test
    void readsLabelledValuesKeyedByTheFoldedLabel() {
        Document page = Jsoup.parse("<dl><div><dt>Télétravail</dt><dd> 2 jours </dd></div><div><dt>Démarrage</dt><dd> </dd></div>" +
            "<div><dd>Sans libellé</dd></div><div><dt> </dt><dd>Sans nom</dd></div></dl>");

        assertThat(PageFacts.of(page.select("dl > div"), "dt", "dd")).containsExactly(Map.entry("teletravail", "2 jours"));
    }

    @Test
    void readsFactsWrittenWithAColon() {
        Document page = Jsoup.parse("<ul><li>TJM : 550 €</li><li>Localisation : Lyon : Part-Dieu</li><li>Sans deux-points</li><li>Vide :</li></ul>");

        assertThat(PageFacts.written(page.select("li"))).hasSize(2)
            .containsEntry("tjm", "550 €").containsEntry("localisation", "Lyon : Part-Dieu");
    }
}
