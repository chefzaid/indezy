package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.SiteResponseException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Next Mondays, Mindquest, Freelance Factory, KatchMe and LittleBig Connection, read from samples of their pages. */
class StructuredSitesReaderTest {

    @Test
    void readsNextMondaysJobsWithTheirSummary() throws Exception {
        String job = "https://nextmondays.com/jobs/technicien-support-applicatif/03u712452";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(NextMondaysReader.SITEMAP, "nextmondays-sitemap.xml")
            .resource(job, "nextmondays-job.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        NextMondaysReader reader = new NextMondaysReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("03u712452");
        ScrapedOffer offer = batch.offers.get(0);
        assertThat(offer.getTitle()).isEqualTo("Technicien Support Applicatif – N2");
        assertThat(offer.getDescription()).isEqualTo("Rattaché(e) à l’équipe applicative");
        assertThat(offer.getDurationMonths()).isEqualTo(6);
        assertThat(offer.getRateText()).isEqualTo("250.00 €/ j");
        assertThat(offer.getCity()).isEqualTo("Paris");
        assertThat(offer.isFreelance()).isTrue();
        assertThat(offer.isPublishedDayOnly()).isTrue();
        assertThat(reader.site()).isEqualTo(OfferSite.NEXT_MONDAYS);
        assertThat(reader.domain()).isEqualTo("nextmondays.com");
    }

    @Test
    void readsMindquestMissionsWithTheirTagsStartAndRate() throws Exception {
        String base = "https://mindquest.io/fr/missions-freelance-offres-emploi-it-finance/";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(MindquestReader.SITEMAP, "mindquest-sitemap.xml")
            .resource(base + "94436/architecte-d-entreprise-hf-59", "mindquest-mission.html")
            .page(base + "94428/administrateur-sap-hf-93", "<html><body>Mission pourvue</body></html>");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        MindquestReader reader = new MindquestReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("94436", "94428");
        ScrapedOffer architect = batch.offers.get(0);
        assertThat(architect.isFreelance()).isTrue();
        assertThat(architect.getPostalCode()).isEqualTo("59000");
        assertThat(architect.getSkills()).containsExactly("Infrastructure & Cloud computing", "Togaf");
        assertThat(architect.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(architect.getRateText()).isEqualTo("600 €/jour");
        assertThat(architect.getExpiresAt()).isEqualTo(Instant.parse("2027-01-16T21:13:31.670Z"));
        assertThat(batch.offers.get(1).isFreelance()).isFalse();
        assertThat(reader.site()).isEqualTo(OfferSite.MINDQUEST);
        assertThat(reader.domain()).isEqualTo("mindquest.io");
    }

    @Test
    void readsFreelanceFactoryMissionsFromTheApiAndTheirPage() throws Exception {
        FakeFetcher fetcher = new FakeFetcher()
            .resource(FreelanceFactoryReader.API, "freelance-factory-api.json")
            .resource("https://freelance-factory.fr/poste/architecte-urbaniste/", "freelance-factory-mission.html")
            .resource("https://freelance-factory.fr/poste/data-scientist/", "freelance-factory-mission.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of("13101"));
        FreelanceFactoryReader reader = new FreelanceFactoryReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("13217");
        ScrapedOffer offer = batch.offers.get(0);
        assertThat(offer.getTitle()).isEqualTo("Architecte – Urbaniste");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-10-14T12:29:17Z"));
        assertThat(offer.getDepartmentName()).isEqualTo("Hauts de Seine");
        assertThat(offer.getRateText()).isEqualTo("600 €");
        assertThat(offer.getDurationMonths()).isEqualTo(6);
        assertThat(offer.getDescription()).isEqualTo("Contexte de la mission : analyse de l'existant.\n\n• Cartographie");
        assertThat(offer.isFreelance()).isTrue();
        assertThat(reader.site()).isEqualTo(OfferSite.FREELANCE_FACTORY);
        assertThat(reader.domain()).isEqualTo("freelance-factory.fr");
    }

    @Test
    void readsKatchMeOpenFreelanceOffersWithTheirOwnFacts() throws Exception {
        String offer = "https://www.katchme.fr/offre/ingenieur-reseau-securite-palo-alto-prisma-access/";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(KatchMeReader.API, "katchme-api.json")
            .resource(offer, "katchme-offer.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        KatchMeReader reader = new KatchMeReader();

        reader.read(fetcher, batch);

        // The CDI offer and the filled one are never fetched.
        assertThat(fetcher.requests()).containsExactly(KatchMeReader.API, offer);
        ScrapedOffer network = batch.offers.get(0);
        assertThat(batch.offers).hasSize(1);
        assertThat(network.getExternalId()).isEqualTo("ingenieur-reseau-securite-palo-alto-prisma-access");
        assertThat(network.getTitle()).isEqualTo("Ingénieur Réseau & Sécurité – Palo Alto Prisma Access");
        assertThat(network.getPublishedAt()).isEqualTo(Instant.parse("2026-10-12T16:13:55Z"));
        assertThat(network.getCity()).isEqualTo("Massy");
        assertThat(network.getRateText()).isEqualTo("TJM 400-550€");
        assertThat(network.getRemoteText()).isEqualTo("Télétravail partiel");
        assertThat(network.getDescription()).isEqualTo("Infos sur l’entreprise\n\nNotre client recherche un(e) Ingénieur Réseau & " +
            "Sécurité maîtrisant Palo Alto Prisma Access.\n\nLes missions\n\n• Concevoir des architectures réseau et sécurité.");
        assertThat(network.isFreelance()).isTrue();
        assertThat(reader.offerId(offer)).contains("ingenieur-reseau-securite-palo-alto-prisma-access");
        assertThat(reader.site()).isEqualTo(OfferSite.KATCHME);
        assertThat(reader.domain()).isEqualTo("katchme.fr");
    }

    @Test
    void readsAKatchMeOfferFromItsPageAloneAndTellsItsContract() throws Exception {
        String permanent = "https://www.katchme.fr/offre/controleur-de-gestion/";
        FakeFetcher fetcher = new FakeFetcher().page(permanent, "<html><body><div id=\"topbtn\"><h2 class=\"elementor-heading-title\">" +
            "Contrôleur de Gestion</h2></div><ul><li><span class=\"elementor-post-info__terms-list-item\">Hauts-de-Seine</span></li>" +
            "<li><span class=\"elementor-post-info__terms-list-item\">45-50K€</span></li>" +
            "<li><span class=\"elementor-post-info__terms-list-item\">CDI</span></li></ul></body></html>");

        ScrapedOffer offer = new KatchMeReader().readOne(fetcher, permanent).orElseThrow();

        assertThat(offer.getTitle()).isEqualTo("Contrôleur de Gestion");
        assertThat(offer.getCity()).isEqualTo("Hauts-de-Seine");
        assertThat(offer.isFreelance()).isFalse();
        assertThat(new KatchMeReader().offer(null, Jsoup.parse("<html><body><h1>Offres</h1></body></html>"))).isNull();
    }

    @Test
    void readsLittleBigConnectionMissionsFromEveryCategory() throws Exception {
        FakeFetcher fetcher = new FakeFetcher();
        for (String category : LittleBigConnectionReader.CATEGORIES) {
            fetcher.page(LittleBigConnectionReader.CATEGORY_URL + category,
                "<script>self.__next_f.push([1,\"{\\\"rfps\\\":[]}\"])</script>");
        }
        fetcher.resource(LittleBigConnectionReader.CATEGORY_URL + "software-engineering", "lbc-category.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        LittleBigConnectionReader reader = new LittleBigConnectionReader();

        reader.read(fetcher, batch);

        assertThat(fetcher.requests()).hasSize(LittleBigConnectionReader.CATEGORIES.size());
        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId)
            .containsExactly("4833c36e-e5ed-4e30-a4a5-adcd4f1257b3", "4ade0015-5d82-480e-a94c-b82c12ebda14");
        ScrapedOffer java = batch.offers.get(0);
        assertThat(java.getTitle()).isEqualTo("Développeur Java \"senior\"");
        assertThat(java.getCity()).isEqualTo("Bordeaux");
        assertThat(java.getCountryCode()).isEqualTo("FR");
        assertThat(java.getRemoteText()).isEqualTo("Hybride");
        assertThat(java.getStartDate()).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(java.getRateMin()).isEqualTo(400);
        assertThat(java.getRateMax()).isEqualTo(500);
        assertThat(java.getPublishedAt()).isNull();
        ScrapedOffer itsm = batch.offers.get(1);
        assertThat(itsm.getCountryCode()).isEqualTo("ES");
        assertThat(itsm.getRateMin()).isNull();
        assertThat(reader.newestFirst()).isFalse();
        assertThat(reader.site()).isEqualTo(OfferSite.LITTLE_BIG_CONNECTION);
        assertThat(reader.domain()).isEqualTo("littlebigconnection.com");
    }

    @Test
    void skipsLittleBigConnectionCategoriesWithoutMissions() throws Exception {
        FakeFetcher fetcher = new FakeFetcher();
        for (String category : LittleBigConnectionReader.CATEGORIES) {
            if (!"cybersecurity".equals(category)) {
                fetcher.page(LittleBigConnectionReader.CATEGORY_URL + category, "<script>self.__next_f.push([1,\"{\\\"rfps\\\":[]}\"])</script>");
            }
        }
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());

        new LittleBigConnectionReader().read(fetcher, batch);

        // Every category is asked for; the missing one answers 404 and is passed over.
        assertThat(fetcher.requests()).hasSize(LittleBigConnectionReader.CATEGORIES.size());
        FakeFetcher failing = new FakeFetcher() {
            @Override
            public String get(String url) throws IOException {
                throw new SiteResponseException(503, "GET answered 503");
            }
        };
        assertThatThrownBy(() -> new LittleBigConnectionReader().read(failing, batch)).isInstanceOf(SiteResponseException.class);
    }

    @Test
    void refusesALittleBigConnectionPageWithoutMissions() throws Exception {
        assertThatThrownBy(() -> LittleBigConnectionReader.missions("<html><script>self.__next_f.push([1,\"nothing\"])</script>"))
            .isInstanceOf(IOException.class);
        assertThat(LittleBigConnectionReader.offer(Json.parse("{\"location\":\"Lyon\"}")).getCity()).isEqualTo("Lyon");
    }

    @Test
    void completesNextMondaysJobsFromTheSummaryOnlyWhereThePostingIsSilent() {
        String page = "<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"Data analyst\",\"employmentType\":\"CONTRACTOR\"," +
            "\"baseSalary\":{\"currency\":\"EUR\",\"value\":{\"unitText\":\"DAY\",\"value\":480}}}</script>" +
            summaryItem("localisation", "Nantes") + summaryItem("durée du contrat", "à définir") + summaryItem("Tarif indicatif", "300 €/ j") +
            summaryItem("vide", " ") + "<div class=\"summary-content-item\"><div class=\"summary-content-item-body\">Sans titre</div></div>";
        NextMondaysReader reader = new NextMondaysReader();

        ScrapedOffer offer = reader.offer(null, Jsoup.parse(page));

        assertThat(offer.getCity()).isEqualTo("Nantes");
        assertThat(offer.getRateMin()).isEqualTo(480);
        assertThat(offer.getRateText()).isNull();
        assertThat(offer.getDurationMonths()).isNull();
        assertThat(NextMondaysReader.summary(Jsoup.parse(page))).containsOnlyKeys("localisation", "duree du contrat", "tarif indicatif");
        assertThat(reader.offer(null, Jsoup.parse("<p>Offre pourvue</p>"))).isNull();
    }

    @Test
    void readsTheMindquestRateOnlyWhenThePostingHasNone() {
        String posting = "<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"Dev\",\"employmentType\":\"CONTRACTOR\"%s}</script>";
        String daily = ",\"baseSalary\":{\"value\":{\"unitText\":\"DAY\",\"value\":650}}";
        MindquestReader reader = new MindquestReader();

        ScrapedOffer rated = reader.offer(null, Jsoup.parse(posting.formatted(daily) +
            "<div><span>à partir de 3 nov</span></div><div><span>700 €/jour</span></div>"));
        ScrapedOffer onSite = reader.offer(null, Jsoup.parse(posting.formatted("") +
            "<div><span>à partir de 3 nov</span></div><div><span>Sur site</span></div>"));
        ScrapedOffer last = reader.offer(null, Jsoup.parse(posting.formatted("") + "<div><span>à partir de 3 nov</span></div>"));
        ScrapedOffer undated = reader.offer(null, Jsoup.parse(posting.formatted("")));

        assertThat(rated.getRateMin()).isEqualTo(650);
        assertThat(rated.getRateText()).isNull();
        assertThat(rated.getStartDate()).satisfies(start -> assertThat(start.getMonthValue() * 100 + start.getDayOfMonth()).isEqualTo(1103));
        assertThat(onSite.getRateText()).isNull();
        assertThat(last.getRateText()).isNull();
        assertThat(undated.getStartDate()).isNull();
        assertThat(undated.getSkills()).isEmpty();
        assertThat(reader.offer(null, Jsoup.parse("<p>Mission pourvue</p>"))).isNull();
    }

    @Test
    void readsFreelanceFactoryListsAndPagesWithMissingParts() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().page(FreelanceFactoryReader.API,
            "[{\"id\":7,\"link\":\"https://freelance-factory.fr/poste/sans-titre/\",\"date_gmt\":\"14/10/2026\"},{\"id\":8},{\"link\":\"x\"}]");
        FreelanceFactoryReader reader = new FreelanceFactoryReader();

        List<DetailPagesReader.Entry> entries = reader.entries(fetcher);
        ScrapedOffer offer = reader.offer(entries.get(0), Jsoup.parse("<ul>" +
            "<li class=\"elementor-icon-list-item\"><span class=\"elementor-icon-list-text\">Sans icône</span></li>" +
            "<li class=\"elementor-icon-list-item\"><svg class=\"e-fas-map-marker\"></svg></li>" +
            "<li class=\"elementor-icon-list-item\"><svg class=\"e-fas-briefcase\"></svg><span class=\"elementor-icon-list-text\">Freelance</span></li>" +
            "</ul>"));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.title()).isNull();
            assertThat(entry.publishedAt()).isNull();
            assertThat(entry.lastModified()).isNull();
        });
        assertThat(offer.getDescription()).isNull();
        assertThat(offer.getDepartmentName()).isNull();
        assertThat(offer.getRateText()).isNull();
        assertThat(offer.isFreelance()).isTrue();
    }

    @Test
    void readsLittleBigConnectionPagesUntilTheBatchHasEnough() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource(LittleBigConnectionReader.CATEGORY_URL + LittleBigConnectionReader.CATEGORIES.get(0), "lbc-category.html");

        new LittleBigConnectionReader().read(fetcher, offers -> false);

        assertThat(fetcher.requests()).hasSize(1);
        assertThat(LittleBigConnectionReader.missions("<script>self.__next_f.push([1,\"{\\\"rfps\\\":[]}\"])</script>" +
            "<script>self.__next_f.push([1,\"cut short")).isEmpty();
        ScrapedOffer budget = LittleBigConnectionReader.offer(Json.parse(
            "{\"url\":\"https://www.littlebigconnection.com/fr/rfp/abc\",\"budget\":{\"unit\":\"total\",\"min\":5000}}"));
        assertThat(budget.getExternalId()).isEqualTo("abc");
        assertThat(budget.getCity()).isNull();
        assertThat(budget.getRateMin()).isNull();
    }

    private static String summaryItem(String title, String body) {
        return "<div class=\"summary-content-item\"><h5 class=\"summary-content-item-title\">" + title + "</h5>" +
            "<div class=\"summary-content-item-body\">" + body + "</div></div>";
    }

    @Test
    void readsKickloxMissionsThroughItsApiAndSetsItsPartnersJobsAside() throws Exception {
        String freelance = "13495fce-95e1-49f9-b9c6-8bd681f406c8";
        String job = "00156822-842b-4664-abe7-4f688b182e65";
        String gone = "0012069c-e556-4b4e-9af3-2b2f2217a38b";
        FakeFetcher fetcher = new FakeFetcher().resource(KickloxReader.SITEMAP, "kicklox-sitemap.xml")
            .post(KickloxReader.API, freelance, FakeFetcher.read("kicklox-mission.json"))
            .post(KickloxReader.API, job, FakeFetcher.read("kicklox-job.json"))
            .post(KickloxReader.API, gone, "{\"data\":{\"mission\":null},\"errors\":[{\"message\":\"not_found\"}]}");
        KickloxReader reader = new KickloxReader();
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());

        reader.read(fetcher, batch);

        // Its pages are never fetched: they render in the browser from the API, asked once for each mission. The day most
        // of the sitemap shares is when its partners' jobs were imported again: those are read after the missions.
        assertThat(fetcher.requests()).containsExactly(KickloxReader.SITEMAP, KickloxReader.API, KickloxReader.API, KickloxReader.API,
            KickloxReader.API, KickloxReader.API);
        assertThat(fetcher.postedBodies()).allSatisfy(body -> assertThat(Json.parse(body).path("query").asString()).startsWith("query getMissionOffline"));
        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId)
            .containsExactly(freelance, gone, job, "6cb19dd2-41bd-4dbb-a64c-ace40af6e5ce", "d615392c-09b2-491a-b37b-674cbecc7430");
        assertThat(batch.offers).extracting(ScrapedOffer::isFreelance).containsExactly(true, false, false, false, false);
        ScrapedOffer mission = batch.offers.get(0);
        assertThat(mission.getTitle()).isEqualTo("Développeur Front AngularJS - Assurance - Lille (H/F)");
        assertThat(mission.getUrl()).isEqualTo("https://app.kicklox.com/missions/" + freelance);
        // A client kept confidential, a placeholder goal left out.
        assertThat(mission.getCompany()).isNull();
        assertThat(mission.getDescription()).isEqualTo("Contexte du poste\n\nNous sommes une entreprise spécialisée dans les solutions " +
            "technologiques pour les assureurs.\n\nProfil recherché\n\n- Au moins 4 ans d'expérience en développement front-end.");
        assertThat(mission.getRateMin()).isEqualTo(400);
        assertThat(mission.getRateMax()).isEqualTo(450);
        assertThat(mission.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(mission.getCity()).isEqualTo("Lille");
        assertThat(mission.getCountryCode()).isEqualTo("FR");
        assertThat(mission.getStartAsap()).isTrue();
        assertThat(mission.getDurationMonths()).isEqualTo(3);
        assertThat(mission.getSkills()).containsExactly("AngularJS", "Web Component", "Jira");
        assertThat(mission.getPublishedAt()).isEqualTo(Instant.parse("2026-09-04T12:30:29Z"));
        assertThat(batch.offers.get(2).getCompany()).isEqualTo("LORELEC");
    }

    @Test
    void readsKickloxRemotePoliciesStartsAndPublishers() throws Exception {
        String mission = "{\"status\":\"published\",\"job\":{\"name\":\"Ingénieur Linux\"},\"contract_types\":[\"missions\"],%s}";
        ScrapedOffer remote = KickloxReader.offer(Json.parse(mission.formatted("\"modalities\":\"remote\",\"company\":{\"name\":\"Easy Partner\"}")));
        ScrapedOffer onSite = KickloxReader.offer(Json.parse(mission.formatted(
            "\"modalities\":\"on_site\",\"begin\":\"precise\",\"begin_at\":\"2026-10-26T00:00:00.000+01:00\",\"daily_rate\":[500]")));
        ScrapedOffer unknown = KickloxReader.offer(Json.parse(mission.formatted("\"modalities\":null,\"begin\":\"precise\"")));

        assertThat(remote.getWorkMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(remote.getRemoteDaysPerWeek()).isEqualTo(5);
        assertThat(remote.getCompany()).isEqualTo("Easy Partner");
        assertThat(onSite.getWorkMode()).isEqualTo(WorkMode.ONSITE);
        assertThat(onSite.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(onSite.getStartAsap()).isNull();
        assertThat(onSite.getRateMin()).isNull();
        assertThat(unknown.getWorkMode()).isNull();
        assertThat(unknown.getStartDate()).isNull();
        assertThat(unknown.getDescription()).isNull();
        // Closed, no longer published, or a standing ad for no mission in particular.
        assertThat(KickloxReader.offer(Json.parse("{\"status\":\"archived\",\"job\":{\"name\":\"Ingénieur Linux\"}}"))).isNull();
        assertThat(KickloxReader.offer(Json.parse(mission.formatted("\"generic_offer\":true")))).isNull();
        assertThat(KickloxReader.offer(Json.parse("{\"status\":\"published\"}"))).isNull();
        KickloxReader reader = new KickloxReader();
        assertThat(reader.offerId("https://app.kicklox.com/missions/13495fce-95e1-49f9-b9c6-8bd681f406c8")).contains("13495fce-95e1-49f9-b9c6-8bd681f406c8");
        assertThat(reader.offerId("https://app.kicklox.com/missions-offres-emploi/freelance")).isEmpty();
        assertThat(reader.apiDomain()).contains("kicklox.io");
    }

    @Test
    void readsRemoteWordsOfTheseSites() {
        assertThat(OfferText.remote("À distance").mode()).isEqualTo(WorkMode.REMOTE);
        assertThat(OfferText.remote("Télétravail 2 jours min.").daysPerWeek()).isEqualTo(2);
    }
}
