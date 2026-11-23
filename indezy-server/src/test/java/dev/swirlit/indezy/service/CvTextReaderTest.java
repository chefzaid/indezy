package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.ProfileImportAiService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class CvTextReaderTest {

    static final String CV = """
        Alice Martin
        Lead développeuse Java
        12 rue de la République, 69002 Lyon
        06.12.34.56.78 · alice@example.test
        linkedin.com/in/alice-martin · https://github.com/alice · www.alice.dev

        Profil
        Dix ans de Java et de Kafka
        pour la banque et l'énergie.

        Compétences techniques
        Langages : Java, Kotlin, SQL
        Frameworks : Spring Boot, Angular

        Expérience professionnelle
        Lead Java - Alice Conseil
        janv. 2024 – aujourd'hui
        Flux Kafka et Java 21 pour une banque.
        Développeuse Java chez Banque Exemple | mars 2018 - décembre 2023
        Paie et virements en Spring Boot.
        Stagiaire, Startup X
        2017 - 2018

        Formation
        2010 - 2015 : Diplôme d'ingénieur, INSA Lyon
        Lycée du Parc, 2010

        Langues
        Anglais (courant), Espagnol : notions

        Certifications
        AWS Certified Solutions Architect (2024)

        Loisirs
        Escalade
        """;

    @Test
    void readsAPlainCvWithoutAi() {
        ProfileImportAiService.Reading reading = CvTextReader.read(CV, LocalDate.of(2026, 11, 23));

        assertThat(reading.firstName()).isEqualTo("Alice");
        assertThat(reading.lastName()).isEqualTo("Martin");
        assertThat(reading.phone()).isEqualTo("06 12 34 56 78");
        assertThat(reading.city()).isEqualTo("Lyon");
        assertThat(reading.position()).isEqualTo("Lead Java");
        assertThat(reading.company()).isEqualTo("Alice Conseil");
        assertThat(reading.bio()).isEqualTo("Dix ans de Java et de Kafka pour la banque et l'énergie.");
        assertThat(reading.linkedin()).isEqualTo("https://linkedin.com/in/alice-martin");
        assertThat(reading.github()).isEqualTo("https://github.com/alice");
        assertThat(reading.website()).isEqualTo("https://www.alice.dev");
        assertThat(reading.languages()).containsExactly("Anglais", "Espagnol");
        // The skills section, then the known skills named elsewhere (AWS, in the certifications).
        assertThat(reading.skills()).extracting(ProfileImportAiService.Skill::name).containsExactly("Java", "Kotlin", "SQL", "Spring Boot", "Angular", "AWS");
        // Java from March 2018 to now, the overlap counted once.
        assertThat(reading.skills()).filteredOn(skill -> skill.name().equals("Java")).extracting(ProfileImportAiService.Skill::years).containsExactly(8);
        assertThat(reading.positions()).extracting(position -> position.employer() + "/" + position.title() + "/" + position.startsOn() + "/" +
            position.endsOn()).containsExactly("Alice Conseil/Lead Java/2024-01-01/null", "Banque Exemple/Développeuse Java/2018-03-01/2023-12-01",
            "Startup X/Stagiaire/2017-01-01/2018-01-01");
        assertThat(reading.education()).containsExactly(new ProfileImportAiService.School("INSA Lyon", "Diplôme d'ingénieur", 2010, 2015),
            new ProfileImportAiService.School("Lycée du Parc", null, 0, 2010));
        assertThat(reading.certifications()).extracting(certification -> certification.name() + "/" + certification.obtainedOn())
            .containsExactly("AWS Certified Solutions Architect/2024-01-01");
    }

    @Test
    void readsDatesHeadingsAndPlaces() {
        assertThat(CvTextReader.period("depuis mars 2022")).isEqualTo(new LinkedInProfilePdf.Period(YearMonth.of(2022, 3), null));
        assertThat(CvTextReader.period("03/2019 à 06/2021")).isEqualTo(new LinkedInProfilePdf.Period(YearMonth.of(2019, 3), YearMonth.of(2021, 6)));
        assertThat(CvTextReader.period("Lyon - Paris")).isNull();
        assertThat(CvTextReader.city("Paris, France")).isEqualTo("Paris");
        assertThat(CvTextReader.city("92400 Courbevoie Cedex")).isEqualTo("Courbevoie");
        assertThat(CvTextReader.city("Java, Kafka")).isNull();
        assertThat(CvTextReader.looksLikeName("Jean-Pierre De La Tour")).isTrue();
        assertThat(CvTextReader.looksLikeName("Compétences")).isFalse();
        assertThat(CvTextReader.looksLikeName("développeur Java")).isFalse();
    }

    @Test
    void readsNothingFromWhatIsNotACv() {
        ProfileImportAiService.Reading reading = CvTextReader.read("liste de courses\npommes\npoires", LocalDate.of(2026, 11, 23));
        assertThat(reading.firstName()).isNull();
        assertThat(reading.positions()).isEmpty();
        assertThat(reading.skills()).isEmpty();
    }
}
