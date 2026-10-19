package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.OfferBatch;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.SiteResponseException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkDispoReaderTest {

    private static final String JOB = "https://www.workdispo.com/job/";

    private final WorkDispoReader reader = new WorkDispoReader();

    @Test
    void readsUnknownJobsMostRecentlyChangedFirst() throws Exception {
        FakeFetcher fetcher = new FakeFetcher()
            .resource(WorkDispoReader.SITEMAP, "workdispo-sitemap.xml")
            .resource(JOB + "administrateur-linux-h-f", "workdispo-job.html")
            .resource(JOB + "tech-lead-cdi-h-f", "workdispo-cdi.html");
        Batch batch = new Batch(Set.of("deja-vue"));

        reader.read(fetcher, batch);

        assertThat(fetcher.requests()).doesNotContain(JOB + "deja-vue");
        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("tech-lead-cdi-h-f", "administrateur-linux-h-f", "disparue");
        ScrapedOffer cdi = batch.offers.get(0);
        assertThat(cdi.isFreelance()).isFalse();
        assertThat(cdi.getWorkMode()).isEqualTo(WorkMode.ONSITE);
        assertThat(cdi.getStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(cdi.getSeniority()).isEqualTo(Seniority.SENIOR);
        ScrapedOffer linux = batch.offers.get(1);
        assertThat(linux.getUrl()).isEqualTo(JOB + "administrateur-linux-h-f");
        assertThat(linux.isFreelance()).isTrue();
        assertThat(linux.getRemoteText()).isEqualTo("Télétravail 2 jours min.");
        assertThat(linux.getStartAsap()).isTrue();
        assertThat(linux.getSeniority()).isEqualTo(Seniority.CONFIRMED);
        assertThat(linux.getRateMin()).isEqualTo(570);
        // A page gone (404) is handed on as closed, so it is set aside.
        assertThat(batch.offers.get(2).isFreelance()).isFalse();
        assertThat(reader.site()).isEqualTo(OfferSite.WORKDISPO);
        assertThat(reader.domain()).isEqualTo("workdispo.com");
        assertThat(reader.newestFirst()).isFalse();
    }

    @Test
    void readsTenPagesPerBatchAndStopsWhenTheBatchHasEnough() throws Exception {
        String urls = IntStream.range(0, 12).mapToObj(i -> "<url><loc>" + JOB + "offre-" + i + "</loc></url>").collect(Collectors.joining());
        FakeFetcher fetcher = new FakeFetcher().page(WorkDispoReader.SITEMAP, "<urlset>" + urls + "</urlset>");
        IntStream.range(0, 12).forEach(i -> fetcher.resource(JOB + "offre-" + i, "workdispo-job.html"));
        List<Integer> pages = new ArrayList<>();

        reader.read(fetcher, offers -> pages.add(offers.size()) && pages.size() < 1);

        assertThat(pages).containsExactly(DetailPagesReader.PAGES_PER_BATCH);
        assertThat(fetcher.requests()).hasSize(1 + DetailPagesReader.PAGES_PER_BATCH);
    }

    @Test
    void stopsTheRunWhenTheSiteFails() {
        FakeFetcher fetcher = new FakeFetcher() {
            @Override
            public String get(String url) throws IOException {
                if (url.startsWith(JOB)) {
                    throw new SiteResponseException(503, "GET answered 503");
                }
                return super.get(url);
            }
        }.resource(WorkDispoReader.SITEMAP, "workdispo-sitemap.xml");

        assertThatThrownBy(() -> reader.read(fetcher, new Batch(Set.of()))).isInstanceOf(SiteResponseException.class);
    }

    @Test
    void readsTheFactsPanel() {
        assertThat(WorkDispoReader.facts(Jsoup.parse(FakeFetcher.read("workdispo-job.html"))))
            .containsEntry("teletravail", "2 jours min.").containsEntry("contrat", "Freelance").containsEntry("demarrage", "Dès que possible");
    }

    @Test
    void completesAPostingFromTheFactsPanel() {
        String posting = "<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"Développeur Go\"," +
            "\"employmentType\":\"CONTRACTOR\"}</script>";

        ScrapedOffer cdd = reader.offer(null, Jsoup.parse(posting + tiles("Localisation", "Lyon", "Démarrage", "15 janvier 2027", "Contrat", "CDD")));
        ScrapedOffer asap = reader.offer(null, Jsoup.parse(posting + tiles("Démarrage", "ASAP", "Vide", " ")));

        assertThat(cdd.isFreelance()).isFalse();
        assertThat(cdd.getCity()).isEqualTo("Lyon");
        assertThat(cdd.getStartDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(cdd.getSeniority()).isNull();
        assertThat(asap.isFreelance()).isTrue();
        assertThat(asap.getStartAsap()).isTrue();
        assertThat(asap.getWorkMode()).isNull();
        assertThat(WorkDispoReader.facts(Jsoup.parse(tiles("Vide", " ")))).isEmpty();
        assertThat(reader.offer(null, Jsoup.parse("<p>Offre expirée</p>"))).isNull();
    }

    /** A facts panel with these labels and values, plus a tile without label. */
    private static String tiles(String... labelsAndValues) {
        StringBuilder html = new StringBuilder("<dl class=\"wdof-tuiles\"><div class=\"wdof-tuile\"><dd>Sans libellé</dd></div>");
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            html.append("<div class=\"wdof-tuile\"><dt>").append(labelsAndValues[i]).append("</dt><dd>").append(labelsAndValues[i + 1])
                .append("</dd></div>");
        }
        return html.append("</dl>").toString();
    }

    /** Collects every page and knows the given identifiers. */
    static class Batch implements OfferBatch {
        final List<ScrapedOffer> offers = new ArrayList<>();
        private final Set<String> known;

        Batch(Set<String> known) {
            this.known = known;
        }

        @Override
        public boolean add(List<ScrapedOffer> page) {
            offers.addAll(page);
            return true;
        }

        @Override
        public Set<String> known(Collection<String> externalIds) {
            return externalIds.stream().filter(known::contains).collect(Collectors.toSet());
        }
    }
}
