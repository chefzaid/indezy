package dev.swirlit.indezy.integration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.JobOfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice keeps CV profiles, draws each as its dossier and makes one the default; Bob reaches none of hers. */
class CvProfileIntegrationTest extends TwoAccountsIntegrationTest {

    @Autowired
    private JobOfferRepository offers;

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsNamedProfilesDrawnFromTheProfilesFacts() throws IOException {
        call(alice, HttpMethod.PUT, "/users/profile", Map.of("firstName", "Alice", "lastName", "Martin", "email", aliceEmail, "position", "Développeuse",
            "bio", "Dix ans de Java.", "skills", List.of("Java", "Kafka", "Angular")), Map.class);
        long kafka = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Kafka", "dailyRate", 650, "status", "WON",
            "clientId", aliceClientId, "startDate", "2025-03-01", "techStack", "Kafka", "description", "Flux de paie."), Map.class));
        long angular = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Dev Angular", "dailyRate", 550, "status", "WON",
            "clientId", aliceClientId, "startDate", "2023-03-01", "techStack", "Angular", "description", "Front."), Map.class));

        Map<String, Object> sources = call(alice, HttpMethod.GET, "/users/cv-profiles/sources", null, Map.class).getBody();
        assertThat((List<String>) sources.get("skills")).containsExactly("Java", "Kafka", "Angular");
        assertThat((List<Map<String, Object>>) sources.get("missions")).extracting(mission -> ((Number) mission.get("id")).longValue())
            .containsExactly(kafka, angular);

        Map<String, Object> backend = call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", " Backend Kafka ", "headline", "Lead Kafka",
            "skills", List.of("Kafka", "Java"), "missions", List.of(Map.of("id", kafka, "text", "Flux de paie temps réel."))), Map.class).getBody();
        assertThat(backend).containsEntry("name", "Backend Kafka").containsEntry("isDefault", true).containsEntry("language", "FR");
        Map<String, Object> front = call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Front", "language", "EN"), Map.class).getBody();
        assertThat(front).containsEntry("isDefault", false).containsEntry("skills", null);
        long backendId = ((Number) backend.get("id")).longValue();
        long frontId = ((Number) front.get("id")).longValue();

        String drawn = text(call(alice, HttpMethod.GET, "/users/cv-profiles/" + backendId + "/dossier", null, byte[].class).getBody());
        assertThat(drawn).contains("Lead Kafka", "Kafka · Java", "Flux de paie temps réel.", "Alice Corp").doesNotContain("Dev Angular", "Angular");
        // The default draws the plain dossier download too.
        assertThat(text(call(alice, HttpMethod.GET, "/users/dossier", null, byte[].class).getBody())).contains("Flux de paie temps réel.");
        assertThat(text(call(alice, HttpMethod.GET, "/users/cv-profiles/" + frontId + "/dossier?layout=ANONYMOUS", null, byte[].class).getBody()))
            .contains("Skills profile", "Dev Angular").doesNotContain("Alice Corp");

        assertThat(call(alice, HttpMethod.PUT, "/users/cv-profiles/" + frontId + "/default", null, Map.class).getBody()).containsEntry("isDefault", true);
        call(alice, HttpMethod.PUT, "/users/cv-profiles/" + backendId, Map.of("name", "Backend", "summary", "Kafka avant tout."), Map.class);
        List<Map<String, Object>> listed = call(alice, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody();
        assertThat(listed).extracting(profile -> profile.get("name")).containsExactly("Backend", "Front");
        assertThat(listed.getFirst()).containsEntry("isDefault", false).containsEntry("summary", "Kafka avant tout.").containsEntry("skills", null);

        // Bob reaches none of them.
        assertThat(call(bob, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/users/cv-profiles/" + backendId + "/dossier", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/users/cv-profiles/" + backendId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // The default deleted, the oldest one left takes its place; at most 20.
        assertThat(call(alice, HttpMethod.DELETE, "/users/cv-profiles/" + frontId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody()).singleElement()
            .satisfies(profile -> assertThat((Map<String, Object>) profile).containsEntry("isDefault", true));
        for (int index = 0; index < 19; index++) {
            call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Profil " + index), Map.class);
        }
        ResponseEntity<Map> refused = call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "De trop"), Map.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flagsAProfileToReviewWhenAFactItReliesOnChanges() {
        call(alice, HttpMethod.PUT, "/users/profile", Map.of("firstName", "Alice", "lastName", "Martin", "email", aliceEmail, "skills",
            List.of("Java", "Kafka")), Map.class);
        long mission = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Kafka", "dailyRate", 650, "status", "WON",
            "clientId", aliceClientId, "startDate", "2025-03-01", "techStack", "Kafka", "description", "Flux."), Map.class));
        long profile = idOf(call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Backend", "skills", List.of("Kafka", "Java"),
            "missions", List.of(Map.of("id", mission, "text", "Flux de paie temps réel."))), Map.class));
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody()).singleElement()
            .satisfies(listed -> assertThat(listed).containsEntry("changes", List.of()));

        // The mission's stack changes, a skill goes, a mission is won.
        Map<String, Object> project = call(alice, HttpMethod.GET, "/projects/" + mission, null, Map.class).getBody();
        project.remove("contractChecks");
        project.put("techStack", "Kafka, Flink");
        call(alice, HttpMethod.PUT, "/projects/" + mission, project, Map.class);
        call(alice, HttpMethod.PUT, "/users/profile", Map.of("firstName", "Alice", "lastName", "Martin", "email", aliceEmail, "skills", List.of("Java")),
            Map.class);
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Architecte", "dailyRate", 700, "status", "WON", "clientId", aliceClientId,
            "startDate", "2026-11-02"), Map.class);

        List<Map<String, Object>> changes = (List<Map<String, Object>>) ((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/cv-profiles",
            null, List.class).getBody()).getFirst().get("changes");
        assertThat(changes).containsExactly(Map.of("kind", "SKILL_REMOVED", "label", "Kafka", "fields", List.of()),
            Map.of("kind", "ITEM_CHANGED", "label", "Lead Kafka", "fields", List.of("stack")),
            Map.of("kind", "ITEM_ADDED", "label", "Architecte", "fields", List.of()));

        // Reviewed: in step again.
        assertThat(call(alice, HttpMethod.PUT, "/users/cv-profiles/" + profile + "/reviewed", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody()).singleElement()
            .satisfies(listed -> assertThat(listed).containsEntry("changes", List.of()));
        assertThat(call(bob, HttpMethod.PUT, "/users/cv-profiles/" + profile + "/reviewed", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicatesAndArchivesProfilesTheDefaultPassingOn() {
        long backend = idOf(call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Backend", "headline", "Lead Kafka",
            "skills", List.of("Java")), Map.class));
        Map<String, Object> copy = call(alice, HttpMethod.POST, "/users/cv-profiles/" + backend + "/duplicate", null, Map.class).getBody();
        assertThat(copy).containsEntry("name", "Backend (copy)").containsEntry("headline", "Lead Kafka").containsEntry("skills", List.of("Java"))
            .containsEntry("isDefault", false).containsEntry("archived", false);
        long copyId = ((Number) copy.get("id")).longValue();

        // The default archived: the other becomes the default; an archived one cannot be the default.
        Map<String, Object> archived = call(alice, HttpMethod.PUT, "/users/cv-profiles/" + backend + "/archived", Map.of("archived", true),
            Map.class).getBody();
        assertThat(archived).containsEntry("archived", true).containsEntry("isDefault", false);
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody())
            .filteredOn(profile -> ((Number) profile.get("id")).longValue() == copyId).singleElement()
            .satisfies(profile -> assertThat(profile).containsEntry("isDefault", true));
        assertThat(call(alice, HttpMethod.PUT, "/users/cv-profiles/" + backend + "/default", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Archived ones are out of the limit of 20 active; brought back, within it.
        for (int index = 0; index < 19; index++) {
            call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Profil " + index), Map.class);
        }
        assertThat(call(alice, HttpMethod.PUT, "/users/cv-profiles/" + backend + "/archived", Map.of("archived", false), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.POST, "/users/cv-profiles/" + copyId + "/duplicate", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        call(alice, HttpMethod.DELETE, "/users/cv-profiles/" + copyId, null, String.class);
        assertThat(call(alice, HttpMethod.PUT, "/users/cv-profiles/" + backend + "/archived", Map.of("archived", false), Map.class).getBody())
            .containsEntry("archived", false);
        assertThat(call(bob, HttpMethod.POST, "/users/cv-profiles/" + backend + "/duplicate", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void picksTheProfileClosestToAFeedOffer() {
        call(alice, HttpMethod.PUT, "/users/profile", Map.of("firstName", "Alice", "lastName", "Martin", "email", aliceEmail, "skills",
            List.of("Java", "Kafka", "Angular")), Map.class);
        JobOffer offer = new JobOffer();
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId("cv-" + java.util.UUID.randomUUID());
        offer.setUrl("https://www.free-work.com/fr/tech-it/job-mission/angular/" + java.util.UUID.randomUUID());
        offer.setTitle("Lead Angular");
        offer.setSkills(List.of("Angular"));
        offer.setSearchText("lead angular");
        java.time.Instant now = java.time.Instant.now();
        offer.setPublishedAt(now);
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        offer.setFirstSeenAt(now);
        offer.setLastSeenAt(now);
        long offerId = offers.save(offer).getId();
        assertThat(call(alice, HttpMethod.GET, "/users/cv-profiles/closest?offerId=" + offerId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);

        call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Backend", "skills", List.of("Java", "Kafka")), Map.class);
        call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Front", "skills", List.of("Angular")), Map.class);
        assertThat(call(alice, HttpMethod.GET, "/users/cv-profiles/closest?offerId=" + offerId, null, Map.class).getBody())
            .containsEntry("name", "Front");
        assertThat(call(alice, HttpMethod.GET, "/users/cv-profiles/closest?offerId=0", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        offers.deleteById(offerId);
    }
}
