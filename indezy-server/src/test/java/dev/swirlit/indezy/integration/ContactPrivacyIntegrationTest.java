package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A contact of Alice's asks what she holds about them, then to be erased: their details, legal basis, exchanges and
 * links handed out without the others' names, the contact and its exchanges gone, both requests in Alice's activity by
 * id only; Bob can do neither.
 */
class ContactPrivacyIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void handsOutThenErasesWhatAliceHoldsAboutAContact() {
        long jean = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Jean", "lastName", "Dupont", "email",
            "jean@esn.example", "phone", "0601020304", "notes", "Met at Devoxx", "clientId", aliceClientId, "legalBasis", "CONSENT", "freelanceId", alice.freelanceId()),
            Map.class));
        long other = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Capgemini", "city", "Paris", "isFinal", false,
            "freelanceId", alice.freelanceId()),
            Map.class));
        long marie = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Marie", "lastName", "Curie", "clientId", other,
            "freelanceId", alice.freelanceId()),
            Map.class));
        assertThat(call(alice, HttpMethod.GET, "/contacts/" + marie, null, Map.class).getBody()).containsEntry("legalBasis", "LEGITIMATE_INTEREST");
        // A change that names no basis keeps it.
        Map<String, Object> change = new HashMap<>(call(alice, HttpMethod.GET, "/contacts/" + jean, null, Map.class).getBody());
        change.remove("legalBasis");
        change.put("phone", "0611223344");
        assertThat(call(alice, HttpMethod.PUT, "/contacts/" + jean, change, Map.class).getBody()).containsEntry("legalBasis", "CONSENT");
        // A field emptied in the form is cleared.
        Map<String, Object> marieChange = new HashMap<>(call(alice, HttpMethod.GET, "/contacts/" + marie, null, Map.class).getBody());
        marieChange.put("lastName", null);
        assertThat(call(alice, HttpMethod.PUT, "/contacts/" + marie, marieChange, Map.class).getBody()).containsEntry("lastName", null)
            .containsEntry("fullName", "Marie");
        call(alice, HttpMethod.POST, "/contacts/" + jean + "/interactions", Map.of("occurredOn", LocalDate.now().toString(), "channel",
            "EMAIL", "direction", "OUTGOING", "replied", true, "summary", "Sent my CV"), Map.class);
        call(alice, HttpMethod.PUT, "/contacts/" + jean + "/relations", Map.of("otherId", marie, "note", "Places freelancers with her"),
            Map.class);

        ResponseEntity<Map> data = call(alice, HttpMethod.GET, "/contacts/" + jean + "/personal-data", null, Map.class);
        assertThat(data.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"contact-" + jean + ".json\"");
        assertThat(data.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(data.getBody()).containsEntry("firstName", "Jean").containsEntry("email", "jean@esn.example")
            .containsEntry("phone", "0611223344").containsEntry("notes", "Met at Devoxx").containsEntry("legalBasis", "CONSENT")
            .containsKeys("keptBy", "keptSince", "exportedOn");
        assertThat((List<Map<String, Object>>) data.getBody().get("exchanges")).singleElement()
            .satisfies(exchange -> assertThat(exchange).containsEntry("channel", "EMAIL").containsEntry("summary", "Sent my CV"));
        assertThat((List<Map<String, Object>>) data.getBody().get("worksWith")).singleElement()
            .satisfies(link -> assertThat(link).containsEntry("company", "Capgemini").containsEntry("note", "Places freelancers with her"));
        assertThat(data.getBody().toString()).doesNotContain("Marie", "Curie");

        assertThat(call(bob, HttpMethod.GET, "/contacts/" + jean + "/personal-data", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/contacts/" + jean + "/erase", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.POST, "/contacts/" + jean + "/erase", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/contacts/" + jean, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contact_interactions WHERE contact_id = ?", Integer.class, jean)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contact_relations WHERE first_contact_id = ? OR second_contact_id = ?",
            Integer.class, jean, jean)).isZero();

        List<Map<String, Object>> events = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/activity", null, Map.class)
            .getBody().get("events");
        assertThat(events).extracting(event -> event.get("type") + " " + event.get("detail"))
            .contains("CONTACT_ERASED contact " + jean, "CONTACT_DATA_EXPORTED contact " + jean);
        assertThat(events.toString()).doesNotContain("Jean", "Dupont");
    }
}
