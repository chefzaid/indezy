package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.ProfileImportAiService;
import dev.swirlit.indezy.exception.FieldRejectedException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LinkedInProfileImportTest {

    static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public static byte[] archive() throws IOException {
        return zip(Map.of(
            "Basic_LinkedInDataExport_11-22-2026/Profile.csv", "﻿First Name,Last Name,Maiden Name,Address,Birth Date,Headline,Summary," +
                "Industry,Zip Code,Geo Location,Twitter Handles,Websites,Instant Messengers\n" +
                "Alice,Martin,,,,Lead Java freelance,\"Dix ans de Java,\n\"\"Kafka\"\" et Spring.\",IT,,\"Lyon, Auvergne-Rhône-Alpes, France\",," +
                "\"[PORTFOLIO:https://alice.dev,OTHER:https://github.com/alice]\",\n",
            "Basic_LinkedInDataExport_11-22-2026/PhoneNumbers.csv", "Extension,Number,Type\n,+33 6 12 34 56 78,Mobile\n",
            "Basic_LinkedInDataExport_11-22-2026/Positions.csv", "Company Name,Title,Description,Location,Started On,Finished On\n" +
                "Alice Conseil,Lead Java,Kafka et Java 21,Lyon,Jan 2024,\n" +
                "Banque Exemple,Développeuse Java,\"Java, Spring\",Paris,mars 2018,Dec 2023\n",
            "Basic_LinkedInDataExport_11-22-2026/Skills.csv", "Name\nJava\nKafka\nAngular\njava\n",
            "Basic_LinkedInDataExport_11-22-2026/Languages.csv", "Name,Proficiency\nEnglish,Full professional\nFrançais,Native\nBreton,\n",
            "Basic_LinkedInDataExport_11-22-2026/Certifications.csv", "Name,Url,Authority,Started On,Finished On,License Number\n" +
                "AWS Certified Solutions Architect,,Amazon Web Services,Mar 2024,Mar 2027,ABC\nProfessional Scrum Master I,,Scrum.org,2019,,\n",
            "Basic_LinkedInDataExport_11-22-2026/messages.csv", "never,read\n"));
    }

    @Test
    void readsTheProfileFromLinkedInsArchive() throws IOException {
        ProfileImportAiService.Reading reading = LinkedInProfileImport.read(archive(), LocalDate.of(2026, 11, 22));

        assertThat(reading.firstName()).isEqualTo("Alice");
        assertThat(reading.lastName()).isEqualTo("Martin");
        assertThat(reading.phone()).isEqualTo("+33 6 12 34 56 78");
        assertThat(reading.city()).isEqualTo("Lyon");
        // The running position is the current one.
        assertThat(reading.position()).isEqualTo("Lead Java");
        assertThat(reading.company()).isEqualTo("Alice Conseil");
        assertThat(reading.bio()).isEqualTo("Dix ans de Java,\n\"Kafka\" et Spring.");
        assertThat(reading.website()).isEqualTo("https://alice.dev");
        assertThat(reading.github()).isEqualTo("https://github.com/alice");
        assertThat(reading.languages()).containsExactly("Anglais", "Français", "Breton");
        // Java from March 2018 to now, the overlap counted once; Kafka since January 2024; Angular named nowhere.
        assertThat(reading.skills()).extracting(skill -> skill.name() + ":" + skill.years()).containsExactly("Java:8", "Kafka:2", "Angular:0");
        assertThat(reading.certifications()).containsExactly(
            new ProfileImportAiService.Certification("AWS Certified Solutions Architect", "Amazon Web Services", "2024-03-01", "2027-03-01"),
            new ProfileImportAiService.Certification("Professional Scrum Master I", "Scrum.org", "2019-01-01", null));
    }

    @Test
    void readsCertificationDates() {
        assertThat(ProfileImportAiService.date("2024-03")).isEqualTo("2024-03-01");
        assertThat(ProfileImportAiService.date(" 2024-3-9 ")).isEqualTo("2024-03-09");
        assertThat(ProfileImportAiService.date("2024-02-31")).isNull();
        assertThat(ProfileImportAiService.date("March 2024")).isNull();
        assertThat(ProfileImportAiService.date(null)).isNull();
    }

    @Test
    void readsLinkedInsDates() {
        assertThat(LinkedInProfileImport.month("Mar 2022")).isEqualTo(YearMonth.of(2022, 3));
        assertThat(LinkedInProfileImport.month("févr. 2021")).isEqualTo(YearMonth.of(2021, 2));
        assertThat(LinkedInProfileImport.month("2019-07-01")).isEqualTo(YearMonth.of(2019, 7));
        assertThat(LinkedInProfileImport.month("2015")).isEqualTo(YearMonth.of(2015, 1));
        assertThat(LinkedInProfileImport.month("someday")).isNull();
        assertThat(LinkedInProfileImport.month(null)).isNull();
    }

    @Test
    void findsTheHeaderUnderLinkedInsNotes() {
        assertThat(LinkedInProfileImport.rows("Notes:\n\"When exporting your connection data...\"\n\nName\nJava\n"))
            .containsExactly(Map.of("name", "Java"));
        assertThat(LinkedInProfileImport.rows("nothing,here\n")).isEmpty();
    }

    @Test
    void refusesWhatIsNotLinkedInsArchive() throws IOException {
        assertThatThrownBy(() -> LinkedInProfileImport.read("hello".getBytes(StandardCharsets.UTF_8), LocalDate.of(2026, 11, 22)))
            .isInstanceOf(FieldRejectedException.class);
        assertThatThrownBy(() -> LinkedInProfileImport.read(zip(Map.of("notes.txt", "x")), LocalDate.of(2026, 11, 22)))
            .isInstanceOf(FieldRejectedException.class).hasMessageContaining("Get a copy of your data");
        assertThat(LinkedInProfileImport.read(zip(Map.of("Skills.csv", "Name\nJava\n")), LocalDate.of(2026, 11, 22)).skills())
            .extracting(ProfileImportAiService.Skill::name).isEqualTo(List.of("Java"));
    }
}
