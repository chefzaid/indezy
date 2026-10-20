package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfferTextTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "TJM HT max 600 € (hors éventuels frais de mission) | -   | 600",
        "470                                                | 470 | 470",
        "450-550€/j                                         | 450 | 550",
        "entre 500 et 650 euros par jour                    | 500 | 650",
        "À partir de 550 €                                  | 550 | -",
        "Jusqu'à 700 €/jour                                 | -   | 700",
        "1 200 € HT                                         | 1200 | 1200",
        "600.00                                             | 600 | 600",
        "Démarrage 01/11/2026, TJM 550 €                    | 550 | 550",
        "Mission de 12 mois, 600 € par jour                 | 600 | 600",
        "Selon profil                                       | -   | -",
        "55k€ annuels                                       | -   | -",
        "58000 - 68000                                      | -   | -",
        "Réf 2026-10-14 démarrage 12h30                     | -   | -"
    })
    void readsDailyRates(String text, Integer min, Integer max) {
        OfferText.Rate rate = OfferText.dailyRate(text);

        assertThat(rate.min()).isEqualTo(min);
        assertThat(rate.max()).isEqualTo(max);
    }

    @Test
    void readsNoRateFromNothing() {
        assertThat(OfferText.dailyRate(null)).isEqualTo(OfferText.Rate.NONE);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "Télétravail complet                      | REMOTE | 5",
        "Full remote                              | REMOTE | 5",
        "100% télétravail                         | REMOTE | 5",
        "Télétravail                              | REMOTE | 5",
        "Hybride                                  | HYBRID | -",
        "partial                                  | HYBRID | -",
        "Paris / 2 jours de télétravail / semaine | HYBRID | 2",
        "Télétravail : 1j par semaine             | HYBRID | 1",
        "Hybride (3 jours sur site)               | HYBRID | 2",
        "5 jours sur site                         | ONSITE | 0",
        "Sur site                                 | ONSITE | 0",
        "Sans télétravail                         | ONSITE | 0",
        "Selon le client                          | -      | -"
    })
    void readsRemotePolicies(String text, WorkMode mode, Integer days) {
        OfferText.Remote remote = OfferText.remote(text);

        assertThat(remote.mode()).isEqualTo(mode);
        assertThat(remote.daysPerWeek()).isEqualTo(days);
    }

    /** Sentences of production descriptions on 2026-10-20, from sites that give no work mode. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "Localisation : Nantes- Télétravail : Non ; full présentiel                              | ONSITE | 0",
        "/!\\ Mission basée 100% à Montrouge, pas de télétravail                                 | ONSITE | 0",
        "NB : Mission entièrement sur site / Pas de télétravaille                                | ONSITE | 0",
        "La mission est à réaliser en 100 % présentiel sur site.                                 | ONSITE | 0",
        "Environnement RISE with SAP / AWS — full remote                                         | REMOTE | 5",
        "Télétravail : 2 à 3 jours de télétravail par semaine / On-boarding de deux semaines     | HYBRID | 2",
        "Localisation : Lyon- Télétravail : 1 à 2 jours par semaine après une phase d'on-boarding | HYBRID | 1",
        "pour une mission longue située à Puteaux ( 2 jrs de Télétravail possible)               | HYBRID | 2",
        "Télétravail : 2 jours/semaine Présentiel : 3 jours/semaine                              | HYBRID | 2",
        "Anglais technique. Télétravail 10 jours par mois                                        | HYBRID | 2",
        "à pourvoir à Aix-en-Provence (50% de télétravail)                                        | HYBRID | 2",
        "Télétravail possible jusqu'à 40% du temps de travail, soumis à accord.                  | HYBRID | 2",
        "Localisation : Paris 15 Télétravail partiel possible (présence sur site 3 jours/semaine) | HYBRID | 2",
        "Localisation : Bordeaux Présence sur site : 2 jours par semaine                         | HYBRID | 3",
        "3 jours sur site à Saint-Denis / 2 jours à distance                                      | HYBRID | 2",
        "Rythme : 1 jour sur site / semaine.                                                     | HYBRID | 4",
        "la mission s'effectue en français selon un mode hybride, avec 50 % de présence          | HYBRID | -",
        "Le SI est composé d'un environnement hybride avec une majorité de solutions Cloud       | -      | -",
        "Solide expérience de pilotage de projets en méthodologie hybride (cycle en V et agile)   | -      | -",
        "réglementaire de l'administration publique (absences, congés, temps, télétravail).      | -      | -",
        "Vous intervenez de manière autonome sur site, en suivant un planning quotidien           | -      | -"
    })
    void readsTheRemotePolicyADescriptionStates(String text, WorkMode mode, Integer days) {
        OfferText.Remote remote = OfferText.remoteInText(text);

        assertThat(remote.mode()).isEqualTo(mode);
        assertThat(remote.daysPerWeek()).isEqualTo(days);
    }

    @Test
    void readsNoRemotePolicyFromNothing() {
        assertThat(OfferText.remoteInText(null)).isEqualTo(OfferText.Remote.UNKNOWN);
        assertThat(OfferText.remote(null)).isEqualTo(OfferText.Remote.UNKNOWN);
        assertThat(OfferText.remote("  ")).isEqualTo(OfferText.Remote.UNKNOWN);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "Junior        | JUNIOR",
        "Débutant      | JUNIOR",
        "Confirmé      | CONFIRMED",
        "Intermédiaire | CONFIRMED",
        "intermediate  | CONFIRMED",
        "Senior        | SENIOR",
        "Expert        | EXPERT",
        "N/A           | -"
    })
    void readsSeniorities(String text, Seniority seniority) {
        assertThat(OfferText.seniority(text)).isEqualTo(seniority);
    }

    @Test
    void readsDurationFields() {
        assertThat(OfferText.duration("6 mois")).isEqualTo(6);
        assertThat(OfferText.duration("6+ mois")).isEqualTo(6);
        assertThat(OfferText.duration("Mission 6 à 12 mois · renouvelable")).isEqualTo(6);
        assertThat(OfferText.duration("1 an")).isEqualTo(12);
        assertThat(OfferText.duration("465 jours ouvrés")).isEqualTo(22);
        assertThat(OfferText.duration("À définir")).isNull();
        assertThat(OfferText.duration(null)).isNull();
    }

    @Test
    void recognisesTitlesOfSalariedJobs() {
        assertThat(OfferText.namesSalariedJob("Tech Lead IA Senior LLM – CDI (H/F)")).isTrue();
        assertThat(OfferText.namesSalariedJob("Comptable en CDD 6 mois")).isTrue();
        assertThat(OfferText.namesSalariedJob("ServiceNow Expert - IT - CDI/Freelance")).isFalse();
        assertThat(OfferText.namesSalariedJob("Développeur Java, portage ou CDI")).isFalse();
        assertThat(OfferText.namesSalariedJob("Architecte CDISCOUNT")).isFalse();
        assertThat(OfferText.namesSalariedJob(null)).isFalse();
    }

    @Test
    void readsSenioritiesFromYearsOfExperience() {
        assertThat(OfferText.seniority(null)).isNull();
        assertThat(OfferText.seniorityOfYears(null)).isNull();
        assertThat(OfferText.seniorityOfYears(-1)).isNull();
        assertThat(OfferText.seniorityOfYears(2)).isEqualTo(Seniority.JUNIOR);
        assertThat(OfferText.seniorityOfYears(5)).isEqualTo(Seniority.CONFIRMED);
        assertThat(OfferText.seniorityOfYears(10)).isEqualTo(Seniority.SENIOR);
        assertThat(OfferText.seniorityOfYears(15)).isEqualTo(Seniority.EXPERT);
    }

    @Test
    void convertsDurationsToMonths() {
        assertThat(OfferText.durationMonths(6.0, "month")).isEqualTo(6);
        assertThat(OfferText.durationMonths(3.0, "mois")).isEqualTo(3);
        assertThat(OfferText.durationMonths(1.0, "year")).isEqualTo(12);
        assertThat(OfferText.durationMonths(2.0, "ans")).isEqualTo(24);
        assertThat(OfferText.durationMonths(13.0, "week")).isEqualTo(3);
        assertThat(OfferText.durationMonths(42.0, "jours")).isEqualTo(2);
        assertThat(OfferText.durationMonths(5.0, "day")).isEqualTo(1);
        assertThat(OfferText.durationMonths(6.0, "fortnight")).isNull();
        assertThat(OfferText.durationMonths(null, "month")).isNull();
        assertThat(OfferText.durationMonths(0.0, "month")).isNull();
        assertThat(OfferText.durationMonths(6.0, null)).isNull();
    }

    @Test
    void readsMissionLengthsWrittenInText() {
        assertThat(OfferText.durationInText("Resp compta senior - freelance - 7 mois", true)).isEqualTo(7);
        assertThat(OfferText.durationInText("Expert Java 10 ans", true)).isNull();
        assertThat(OfferText.durationInText("Contexte. Durée : 6 mois renouvelables, mission longue", false)).isEqualTo(6);
        assertThat(OfferText.durationInText("Démarrage ASAP, 12 mois renouvelables", false)).isEqualTo(12);
        assertThat(OfferText.durationInText("Pour une durée initiale de 3 mois", false)).isEqualTo(3);
        assertThat(OfferText.durationInText("Mission de 2 ans chez un assureur", false)).isEqualTo(24);
        assertThat(OfferText.durationInText("Duration: 18 months", false)).isEqualTo(18);
        assertThat(OfferText.durationInText("5 ans d'expérience et 3 mois de préavis", false)).isNull();
        assertThat(OfferText.durationInText(null, false)).isNull();
        assertThat(OfferText.durationInText(" ", true)).isNull();
    }

    @Test
    void placesRemoteDaysOnTheWorkModeScale() {
        assertThat(OfferText.remoteDaysPerWeek(-2)).isEqualTo(new OfferText.Remote(WorkMode.ONSITE, 0));
        assertThat(OfferText.remoteDaysPerWeek(3)).isEqualTo(new OfferText.Remote(WorkMode.HYBRID, 3));
        assertThat(OfferText.remoteDaysPerWeek(7)).isEqualTo(new OfferText.Remote(WorkMode.REMOTE, 5));
    }
}
