package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectiveReaderTest {

    private final CollectiveReader reader = new CollectiveReader();

    @Test
    void readsTheOffersOfTheNextJsPageAndKeepsFreelanceOnly() throws Exception {
        FakeFetcher fetcher = new FakeFetcher()
            .resource(CollectiveReader.LIST_URL + 1, "collective-page.html")
            .page(CollectiveReader.LIST_URL + 2, page(""));
        List<ScrapedOffer> offers = new ArrayList<>();

        reader.read(fetcher, page -> offers.addAll(page));

        assertThat(offers).extracting(ScrapedOffer::getExternalId).containsExactly("cl001", "cl002", "cl003", "cl004");
        assertThat(offers).extracting(ScrapedOffer::isFreelance).containsExactly(true, false, true, false);
        ScrapedOffer angular = offers.get(0);
        assertThat(angular.getUrl()).isEqualTo("https://www.collective.work/jobs/fr/developpeur-angular-ab12");
        assertThat(angular.getCompany()).isEqualTo("Agence Exemple");
        assertThat(angular.getDescription()).isEqualTo("Mission de 9 mois");
        assertThat(angular.getRateText()).isEqualTo("TJM HT max 600 € (hors frais)");
        assertThat(angular.getPostalCode()).isEqualTo("92400");
        assertThat(angular.getCity()).isEqualTo("Courbevoie");
        assertThat(angular.getCountryCode()).isEqualTo("FR");
        assertThat(angular.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(angular.getSkills()).containsExactly("Angular", "Javascript", "Sql queries", "CRM");
        assertThat(angular.getPublishedAt()).isEqualTo(Instant.parse("2026-10-19T16:55:04.968Z"));
        assertThat(angular.getExpiresAt()).isEqualTo(Instant.parse("2026-12-31T22:00:00Z"));
        ScrapedOffer data = offers.get(2);
        assertThat(data.getDescription()).isEqualTo("Spark et Databricks");
        assertThat(data.getWorkMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(data.getCity()).isEqualTo("Bruxelles");
        assertThat(data.getCountryCode()).isEqualTo("BE");
        assertThat(offers.get(1).getCity()).isNull();
    }

    @Test
    void refusesAPageWithoutItsData() {
        FakeFetcher fetcher = new FakeFetcher().page(CollectiveReader.LIST_URL + 1, "<html><body>Bientôt</body></html>");

        assertThatThrownBy(() -> reader.read(fetcher, page -> true)).isInstanceOf(IOException.class)
            .hasMessageContaining("__NEXT_DATA__");
    }

    @Test
    void refusesDataWithoutSearchResults() {
        assertThatThrownBy(() -> CollectiveReader.projects(
            "<script id=\"__NEXT_DATA__\" type=\"application/json\">{\"props\":{}}</script>"))
            .isInstanceOf(IOException.class).hasMessageContaining("search results");
    }

    @Test
    void labelsProjectTypeCodes() {
        assertThat(CollectiveReader.label("DATA_ENGINEERING")).isEqualTo("Data engineering");
        assertThat(CollectiveReader.label("AWS")).isEqualTo("AWS");
        assertThat(CollectiveReader.label(null)).isNull();
        assertThat(reader.site()).isEqualTo(OfferSite.COLLECTIVE);
        assertThat(reader.domain()).isEqualTo("collective.work");
    }

    private static String page(String projects) {
        return "<script id=\"__NEXT_DATA__\" type=\"application/json\">{\"props\":{\"pageProps\":{\"dehydratedState\":" +
            "{\"queries\":[{\"state\":{\"data\":{\"results\":{\"projects\":[" + projects + "]}}}}]}}}}</script>";
    }
}
