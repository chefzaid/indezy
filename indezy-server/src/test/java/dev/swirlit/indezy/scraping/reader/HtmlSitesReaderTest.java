package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.SiteResponseException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Silkhom, LeGratin, Freelance-Informatique, Espace Freelance, Beyond Skills IT, Koïno, Sixième Homme, Castor Network and
 * OuiDesk, read from samples of their pages.
 */
class HtmlSitesReaderTest {

    @Test
    void readsSilkhomFreelanceCardsOnly() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource(SilkhomReader.LIST_URL, "silkhom-list.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        SilkhomReader reader = new SilkhomReader();

        reader.read(fetcher, batch);

        assertThat(fetcher.requests()).containsExactly(SilkhomReader.LIST_URL);
        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("519314", "519280");
        ScrapedOffer iot = batch.offers.get(0);
        assertThat(iot.getTitle()).isEqualTo("Ingénieur Logiciel Embarqué / IoT");
        assertThat(iot.getUrl()).isEqualTo("https://www.silkhom.com/annonce/ingenieur-logiciel-embarque-iot/");
        assertThat(iot.getCity()).isEqualTo("Bordeaux");
        assertThat(iot.getPublishedAt()).isEqualTo(Instant.parse("2026-10-14T22:00:00Z"));
        assertThat(iot.isPublishedDayOnly()).isTrue();
        assertThat(iot.getDescription()).contains("Secteur de l’IoT").contains("Développement du logiciel embarqué");
        ScrapedOffer react = batch.offers.get(1);
        assertThat(react.getUrl()).isEqualTo("https://www.silkhom.com/annonce/developpeur-react/");
        assertThat(react.getPublishedAt()).isNull();
        assertThat(react.getDescription()).isNull();
        assertThat(reader.site()).isEqualTo(OfferSite.SILKHOM);
        assertThat(reader.domain()).isEqualTo("silkhom.com");
    }

    @Test
    void readsLeGratinMissionsDatedByTheirId() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource(LeGratinReader.LIST_URL, "legratin-missions.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        LeGratinReader reader = new LeGratinReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("6ad63054216c85828c5fe428", "6a9ea6ed6064d3d87ada7208");
        ScrapedOffer fullstack = batch.offers.get(0);
        assertThat(fullstack.getUrl()).isEqualTo("https://www.legratin.io/offer/6ad63054216c85828c5fe428");
        assertThat(fullstack.getTitle()).isEqualTo("Développeur Fullstack C# / Angular – IT Risk");
        assertThat(fullstack.getPublishedAt()).isEqualTo(Instant.ofEpochSecond(0x6ad63054L));
        assertThat(fullstack.getRateText()).isEqualTo("607€/j");
        assertThat(fullstack.getDescription()).isEqualTo("Environnement à préciser\n\nContexte : Banque & assurances");
        assertThat(fullstack.getCity()).isEqualTo("Paris");
        assertThat(fullstack.getRemoteText()).isEqualTo("2 jours de télétravail / semaine");
        assertThat(fullstack.getDurationMonths()).isEqualTo(6);
        ScrapedOffer flux = batch.offers.get(1);
        assertThat(flux.getCity()).isEqualTo("Lyon");
        assertThat(flux.getRemoteText()).isNull();
        assertThat(flux.getDescription()).isNull();
        assertThat(reader.site()).isEqualTo(OfferSite.LEGRATIN);
        assertThat(reader.domain()).isEqualTo("legratin.io");
    }

    @Test
    void readsFreelanceInformatiqueOffersPageAfterPage() throws Exception {
        FakeFetcher fetcher = new FakeFetcher()
            .resource(FreelanceInformatiqueReader.LIST_URL + 1, "freelance-informatique-list.html")
            .page(FreelanceInformatiqueReader.LIST_URL + 2, "<html><body><p>Aucune offre</p></body></html>");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        FreelanceInformatiqueReader reader = new FreelanceInformatiqueReader();

        reader.read(fetcher, batch);

        assertThat(fetcher.requests()).containsExactly(FreelanceInformatiqueReader.LIST_URL + 1, FreelanceInformatiqueReader.LIST_URL + 2);
        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("261019C005", "261019C006", "261018N001");
        ScrapedOffer fullstack = batch.offers.get(0);
        assertThat(fullstack.getUrl()).isEqualTo("https://www.freelance-informatique.fr/mission-developpeur-fullstack-sur-paris-261019C005");
        assertThat(fullstack.getPublishedAt()).isEqualTo(Instant.parse("2026-10-18T22:00:00Z"));
        assertThat(fullstack.getSkills()).containsExactly("Angular", ".NET");
        assertThat(fullstack.getDepartmentName()).isEqualTo("Paris");
        assertThat(fullstack.getCity()).isEqualTo("Paris");
        assertThat(fullstack.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(fullstack.getDurationMonths()).isEqualTo(8);
        assertThat(fullstack.getDescription()).startsWith("Nous recherchons un Développeur Fullstack");
        ScrapedOffer workplace = batch.offers.get(1);
        assertThat(workplace.getUrl()).endsWith("/mission-chef-de-projet-workplace-sur-st-denis-261019C006");
        assertThat(workplace.getDepartmentName()).isEqualTo("Seine-Saint-Denis");
        assertThat(workplace.getCity()).isEqualTo("Saint Denis");
        assertThat(workplace.getStartAsap()).isTrue();
        assertThat(workplace.getDurationMonths()).isEqualTo(22);
        ScrapedOffer sap = batch.offers.get(2);
        assertThat(sap.getRemoteText()).isEqualTo("Télétravail");
        assertThat(sap.getCity()).isNull();
        assertThat(sap.getStartDate()).isNull();
        assertThat(reader.site()).isEqualTo(OfferSite.FREELANCE_INFORMATIQUE);
        assertThat(reader.domain()).isEqualTo("freelance-informatique.fr");
    }

    @Test
    void readsEspaceFreelanceMissionsUntilThePagesEnd() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource(EspaceFreelanceReader.LIST_URL, "espace-freelance-list.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        EspaceFreelanceReader reader = new EspaceFreelanceReader();

        // Page 2 answers 404, as WordPress does past the last page.
        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("14136", "14135");
        ScrapedOffer soc = batch.offers.get(0);
        assertThat(soc.getUrl()).isEqualTo("https://www.espace-freelance.fr/missions-disponibles/expert-soc-_-lille-h-f/");
        assertThat(soc.getCity()).isEqualTo("Lille");
        assertThat(soc.getDescription()).isEqualTo("Métier : Ingénieur Sécurité\nAgence : Agence Nord\nRéférence : EFN/EXPSOC/DVHH");
        assertThat(soc.getRateText()).isEqualTo("550 € HT");
        assertThat(soc.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(soc.getPublishedAt()).isEqualTo(Instant.parse("2026-10-13T22:00:00Z"));
        ScrapedOffer network = batch.offers.get(1);
        assertThat(network.getTitle()).isEqualTo("Technicien Systèmes & Réseaux (F/H) – Lille");
        assertThat(network.getCity()).isEqualTo("Lille");
        assertThat(network.getStartAsap()).isTrue();
        assertThat(network.getStartDate()).isNull();
        assertThat(network.getDescription()).isNull();
        assertThat(EspaceFreelanceReader.city("Business Analyst")).isNull();
        assertThatThrownBy(() -> reader.read(new FakeFetcher(), batch)).isInstanceOf(SiteResponseException.class);
        assertThat(reader.site()).isEqualTo(OfferSite.ESPACE_FREELANCE);
        assertThat(reader.domain()).isEqualTo("espace-freelance.fr");
    }

    @Test
    void readsBeyondSkillsFreelanceOffersFromTheirPageDatedBySitemap() throws Exception {
        String architect = BeyondSkillsItReader.LIST_URL + "/architecte-frontend-vuejs-freelance";
        String golang = BeyondSkillsItReader.LIST_URL + "/senior-backend-golang";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(BeyondSkillsItReader.SITEMAP, "beyond-skills-sitemap.xml")
            .resource(BeyondSkillsItReader.LIST_URL, "beyond-skills-list.html")
            .resource(architect, "beyond-skills-offer.html")
            .page(golang, "<html><body><div class=\"offres_title\"><h1>Senior Backend Golang</h1></div></body></html>");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        BeyondSkillsItReader reader = new BeyondSkillsItReader();

        reader.read(fetcher, batch);

        // The CDI offer's page is never read.
        assertThat(fetcher.requests()).containsExactly(BeyondSkillsItReader.SITEMAP, BeyondSkillsItReader.LIST_URL, architect, golang);
        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("architecte-frontend-vuejs-freelance", "senior-backend-golang");
        ScrapedOffer offer = batch.offers.get(0);
        assertThat(offer.getTitle()).isEqualTo("Architecte Frontend Vue.js");
        assertThat(offer.getUrl()).isEqualTo(architect);
        assertThat(offer.getCity()).isEqualTo("Paris");
        assertThat(offer.getRemoteText()).isEqualTo("Remote");
        assertThat(offer.getRateText()).isEqualTo("600-650€/jour");
        assertThat(offer.getSkills()).containsExactly("Vue.js", "TypeScript");
        assertThat(offer.getDescription()).isEqualTo("🏢 L'entreprise\n\nUn éditeur SaaS en forte croissance.\n\n🎯 Le rôle\n\n• Définir l'architecture frontend");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
        assertThat(offer.isPublishedDayOnly()).isTrue();
        assertThat(offer.isFreelance()).isTrue();
        // A page that no longer says Freelance is set aside.
        ScrapedOffer bare = batch.offers.get(1);
        assertThat(bare.isFreelance()).isFalse();
        assertThat(bare.getPublishedAt()).isNull();
        assertThat(bare.getCity()).isNull();
        assertThat(bare.getRateText()).isNull();
        assertThat(reader.offer(new DetailPagesReader.Entry(golang, "x", null), Jsoup.parse("<p>Offre pourvue</p>"))).isNull();
        assertThat(reader.offerId(architect)).contains("architecte-frontend-vuejs-freelance");
        assertThat(reader.site()).isEqualTo(OfferSite.BEYOND_SKILLS_IT);
        assertThat(reader.domain()).isEqualTo("beyondskillsit.com");
    }

    @Test
    void readsKoinoMissionsAndSetsClosedOnesAside() throws Exception {
        String missions = "https://www.koino.fr/missions/";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(KoinoReader.SITEMAP, "koino-sitemap.xml")
            .resource(missions + "data-analyst-data-engineer-urgent", "koino-mission-open.html")
            .resource(missions + "architecte-ia-multi-agents-azure", "koino-mission-closed.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        KoinoReader reader = new KoinoReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId)
            .containsExactly("data-analyst-data-engineer-urgent", "architecte-ia-multi-agents-azure");
        ScrapedOffer data = batch.offers.get(0);
        assertThat(data.isFreelance()).isTrue();
        assertThat(data.getTitle()).isEqualTo("Data Analyst / Data Engineer");
        assertThat(data.getCity()).isEqualTo("Lyon");
        assertThat(data.getRateText()).isEqualTo("550 €");
        assertThat(data.getDescription()).startsWith("Fiabiliser les tableaux de bord d'un distributeur.")
            .contains("Description du client").contains("• Modéliser les ventes").doesNotContain("Candidature");
        assertThat(data.getPublishedAt()).isEqualTo(Instant.parse("2026-10-12T10:10:49.454Z"));
        assertThat(data.isPublishedDayOnly()).isTrue();
        ScrapedOffer closed = batch.offers.get(1);
        assertThat(closed.isFreelance()).isFalse();
        assertThat(closed.getRemoteText()).isEqualTo("Télétravail");
        assertThat(closed.getCity()).isNull();
        assertThat(reader.offer(null, Jsoup.parse("<p>Page introuvable</p>"))).isNull();
        assertThat(reader.site()).isEqualTo(OfferSite.KOINO);
        assertThat(reader.domain()).isEqualTo("koino.fr");
    }

    @Test
    void readsTheMissionsSixiemeHommeShowcases() throws Exception {
        String mission = "https://freelance.sixiemehomme.io/mission/";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(SixiemeHommeReader.HOME_URL, "sixieme-home.html")
            .resource(mission + "5d62b7e4-1497-4d89-89e9-eed726c3e03f", "sixieme-mission.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        SixiemeHommeReader reader = new SixiemeHommeReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId)
            .containsExactly("5d62b7e4-1497-4d89-89e9-eed726c3e03f", "d19e75cd-4427-4e73-99b7-23798f4eb47e");
        ScrapedOffer risk = batch.offers.get(0);
        assertThat(risk.getTitle()).isEqualTo("Consultant risk management");
        assertThat(risk.getSkills()).containsExactly("Risque", "Gestion des risques");
        assertThat(risk.getStartAsap()).isTrue();
        assertThat(risk.getDurationMonths()).isEqualTo(6);
        assertThat(risk.getRateText()).isEqualTo("850€/jour");
        assertThat(risk.getCity()).isEqualTo("Paris");
        assertThat(risk.getRemoteText()).isEqualTo("Télétravail partiel");
        assertThat(risk.getDescription()).startsWith("Secteur de l'énergie").contains("• Registre des risques");
        assertThat(risk.getPublishedAt()).isNull();
        // The second showcased mission is gone (404): handed on as closed.
        assertThat(batch.offers.get(1).isFreelance()).isFalse();
        assertThat(reader.offer(null, Jsoup.parse("<p>Mission introuvable</p>"))).isNull();
        assertThat(reader.site()).isEqualTo(OfferSite.SIXIEME_HOMME);
        assertThat(reader.domain()).isEqualTo("sixiemehomme.io");
    }

    @Test
    void readsCastorNetworkMissionsWithTheirIconFacts() throws Exception {
        FakeFetcher fetcher = new FakeFetcher()
            .resource(CastorNetworkReader.LIST_URL + 1, "castor-list.html")
            .page(CastorNetworkReader.LIST_URL + 2, "<html><body><div class=\"availableMissions\">52 mission(s)</div></body></html>");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        CastorNetworkReader reader = new CastorNetworkReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("3571", "3570");
        ScrapedOffer lead = batch.offers.get(0);
        assertThat(lead.getUrl()).isEqualTo("https://www.castor-network.com/fr/mission-freelance/3571/" +
            "developpement-de-a-a-z-d-un-projet-en-environnement-industriels-production");
        assertThat(lead.getTitle()).isEqualTo("Chef de Projet Industriel");
        assertThat(lead.getDescription()).startsWith("Développement de A à Z d'un projet en environnement industriels/production\n\n" +
            "Chef de Projet Industriel Freelance").contains("Piloter des projets industriels de bout en bout.");
        assertThat(lead.getCompany()).isEqualTo("ALTEN");
        assertThat(lead.getDurationMonths()).isEqualTo(6);
        assertThat(lead.getStartAsap()).isTrue();
        assertThat(lead.getCity()).isEqualTo("Auxerre");
        assertThat(lead.getSeniority()).isEqualTo(Seniority.CONFIRMED);
        assertThat(lead.getPublishedAt()).isEqualTo(Instant.parse("2026-10-11T22:00:00Z"));
        assertThat(lead.isPublishedDayOnly()).isTrue();
        assertThat(lead.isFreelance()).isTrue();
        ScrapedOffer embedded = batch.offers.get(1);
        assertThat(embedded.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(embedded.getStartAsap()).isNull();
        assertThat(embedded.getCompany()).isNull();
        assertThat(fetcher.requests()).containsExactly(CastorNetworkReader.LIST_URL + 1, CastorNetworkReader.LIST_URL + 2);
        assertThat(reader.site()).isEqualTo(OfferSite.CASTOR_NETWORK);
        assertThat(reader.domain()).isEqualTo("castor-network.com");
        assertThat(reader.offers(Jsoup.parse("<div class=\"missionListItem\"><a href=\"/fr/mission-freelance/9/x\"></a>" +
            "<div class=\"missionContainer-missionDescription\"><p>Publiée le 01/10/2026</p></div></div>"))).isEmpty();
    }

    @Test
    void readsOuiDeskMissionsAsTheyDescribeThemselves() throws Exception {
        String mission = "https://ouidesk.com/mission/Consultant-réseaux-et-sécurité-H-F-1196ae9a011ad6a2c1d91fdb23574712";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(OuiDeskReader.SITEMAP, "ouidesk-sitemap.xml")
            .resource(mission, "ouidesk-mission.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        OuiDeskReader reader = new OuiDeskReader();

        reader.read(fetcher, batch);

        // Only the mission page: the talents' profiles and the search pages are left aside.
        assertThat(fetcher.requests()).containsExactly(OuiDeskReader.SITEMAP, mission);
        ScrapedOffer offer = batch.offers.get(0);
        assertThat(batch.offers).hasSize(1);
        assertThat(offer.getExternalId()).isEqualTo("1196ae9a011ad6a2c1d91fdb23574712");
        assertThat(offer.getTitle()).isEqualTo("Consultant réseaux et sécurité H/F");
        assertThat(offer.getCity()).isEqualTo("Lille");
        assertThat(offer.getStartAsap()).isTrue();
        assertThat(offer.getDurationMonths()).isEqualTo(12);
        assertThat(offer.getRateText()).isEqualTo("550 €/jour");
        assertThat(offer.getDescription()).startsWith("Référence:\n\n20-AMOA-QO").contains("Intégré(e) au programme One Network")
            .contains("Déployer SD-WAN FortiGate.").doesNotContain("Rémunération").doesNotContain("SE CONNECTER");
        assertThat(offer.getPublishedAt()).isNull();
        assertThat(offer.isFreelance()).isTrue();
        // The same mission whatever its address's encoding.
        assertThat(reader.offerId("https://ouidesk.com/mission/Consultant-r%c3%a9seaux-et-s%c3%a9curit%c3%a9-H-F-1196ae9a011ad6a2c1d91fdb23574712/"))
            .contains("1196ae9a011ad6a2c1d91fdb23574712");
        assertThat(reader.offerId("https://ouidesk.com/profile/Geoffrey-28a01d2f8adc0a94e3f6e3db37b98406/")).isEmpty();
        assertThat(reader.offer(new DetailPagesReader.Entry(mission, "x", null), Jsoup.parse(
            "<meta name=\"description\" content=\"Poste en CDI\"><h4 class=\"text-blue\">Comptable</h4>")).isFreelance()).isFalse();
        assertThat(reader.offer(new DetailPagesReader.Entry(mission, "x", null), Jsoup.parse("<p>Mission introuvable</p>"))).isNull();
        assertThat(reader.site()).isEqualTo(OfferSite.OUIDESK);
        assertThat(reader.domain()).isEqualTo("ouidesk.com");
    }

    @Test
    void readsCardsWithMissingParts() {
        assertThat(new LeGratinReader().offers(Jsoup.parse("<article><a href=\"/offer/6ad63054216c85828c5fe428\">Voir</a></article>" +
            "<article><h2>Mission</h2><a href=\"/offer/6ad63054216c85828c5fe429\">Voir</a></article>")))
            .singleElement().satisfies(offer -> {
                assertThat(offer.getRateText()).isNull();
                assertThat(offer.getCity()).isNull();
            });
        assertThat(new FreelanceInformatiqueReader().offers(Jsoup.parse("<div class=\"job-card-line\"><p>Sans titre</p></div>" +
            "<div class=\"job-card-line\"><h2 class=\"job-title\">Sans lien</h2></div>" +
            "<div class=\"job-card-line\"><h2 class=\"job-title\"><a href=\"https://ailleurs.example/mission-x-261019C001\">Ailleurs</a></h2></div>" +
            "<div class=\"job-card-line\"><h2 class=\"job-title\"><a href=\"/mission-x-261399C001\">Date impossible</a></h2>" +
            "<ul><li><i class=\"icon icon-calendar\"></i> janvier</li></ul></div>")))
            .singleElement().satisfies(offer -> {
                assertThat(offer.getPublishedAt()).isNull();
                assertThat(offer.getStartAsap()).isNull();
                assertThat(offer.getStartDate()).isNotNull();
            });
        assertThat(new EspaceFreelanceReader().offers(Jsoup.parse("<div class=\"uk-card\"><input class=\"mission_id\" value=\"1\">" +
            "<a href=\"/missions-disponibles/a/\"><h2>Développeur _ </h2></a></div>" +
            "<div class=\"uk-card\"><input class=\"mission_id\" value=\"2\"><a href=\"/missions-disponibles/b/\"><h2>Data</h2></a>" +
            "<ul><li id=\"mission-date-demarrage\">Démarrage : <strong>janvier</strong></li></ul></div>")))
            .extracting(ScrapedOffer::getCity, ScrapedOffer::getPublishedAt, ScrapedOffer::getStartDate, ScrapedOffer::getStartAsap)
            .containsExactly(tuple(null, null, null, null), tuple(null, null, null, null));
        ScrapedOffer bare = new SixiemeHommeReader().offer(null, Jsoup.parse("<div class=\"mission-details-header\"><h1>PMO</h1></div>" +
            "<div class=\"mission-row\"><span class=\"row-label\">Début :</span><span class=\"row-value\">Janvier</span></div>"));
        assertThat(bare.getStartAsap()).isNull();
        assertThat(bare.getRemoteText()).isNull();
        assertThat(bare.getDescription()).isNull();
    }

    @Test
    void stopsListPagesWhenTheBatchHasEnough() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource(FreelanceInformatiqueReader.LIST_URL + 1, "freelance-informatique-list.html");

        new FreelanceInformatiqueReader().read(fetcher, offers -> false);

        assertThat(fetcher.requests()).hasSize(1);
    }
}
