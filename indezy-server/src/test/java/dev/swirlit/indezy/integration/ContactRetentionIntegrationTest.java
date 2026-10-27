package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.service.ContactRetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice keeps her contacts three years without exchange: the dormant ones are anonymized at night, their exchanges and
 * links kept without their summaries and notes; a contact written to lately, or at a company with an opportunity
 * changed lately, stays, and so do Bob's, who set no period.
 */
class ContactRetentionIntegrationTest extends TwoAccountsIntegrationTest {

    @Autowired
    private ContactRetentionService retention;

    private long contact(Account account, String firstName, long clientId) {
        return idOf(call(account, HttpMethod.POST, "/contacts", Map.of("firstName", firstName, "lastName", "Doe", "email", firstName + "@esn.example",
            "phone", "0601020304", "notes", "Some note", "clientId", clientId, "freelanceId", account.freelanceId()), Map.class));
    }

    private void exchange(long contactId, LocalDate on, String summary) {
        call(alice, HttpMethod.POST, "/contacts/" + contactId + "/interactions", Map.of("occurredOn", on.toString(), "channel", "EMAIL",
            "direction", "INCOMING", "summary", summary), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anonymizesTheContactsDormantBeyondTheRetentionPeriod() {
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody()).containsEntry("contactRetentionYears", 0);
        assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("contactRetentionYears", 11), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("contactRetentionYears", 3), Map.class).getBody())
            .containsEntry("contactRetentionYears", 3);

        LocalDate today = LocalDate.now();
        long esn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Old ESN", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        long jean = contact(alice, "Jean", esn);
        long marie = contact(alice, "Marie", esn);
        long paul = contact(alice, "Paul", esn);
        long active = contact(alice, "Active", aliceClientId);
        idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java", "dailyRate", 600, "status", "CONTACT", "clientId", aliceClientId,
            "freelanceId", alice.freelanceId()), Map.class));
        exchange(jean, today.minusYears(4), "Sent my CV");
        exchange(paul, today.minusYears(1), "Called back");
        call(alice, HttpMethod.PUT, "/contacts/" + jean + "/relations", Map.of("otherId", active, "note", "Works with the client"), Map.class);
        long bobClient = idOf(call(bob, HttpMethod.POST, "/clients", Map.of("companyName", "Bob ESN", "city", "Lille", "isFinal", false,
            "freelanceId", bob.freelanceId()), Map.class));
        long bobs = contact(bob, "Bobs", bobClient);
        // Everything was written four years ago, but for the project and the exchanges.
        jdbcTemplate.update("UPDATE contacts SET created_at = ?, updated_at = ? WHERE id IN (?, ?, ?, ?, ?)", LocalDateTime.now().minusYears(4),
            LocalDateTime.now().minusYears(4), jean, marie, paul, active, bobs);

        assertThat(retention.anonymizeDormant(LocalDateTime.now())).isEqualTo(2);

        Map<String, Object> erased = call(alice, HttpMethod.GET, "/contacts/" + jean, null, Map.class).getBody();
        assertThat(erased).containsEntry("firstName", "Anonymized").containsEntry("lastName", null).containsEntry("email", null)
            .containsEntry("phone", null).containsEntry("notes", null).containsKey("anonymizedAt");
        assertThat(erased.get("anonymizedAt")).isNotNull();
        assertThat(call(alice, HttpMethod.GET, "/contacts/" + marie, null, Map.class).getBody()).containsEntry("firstName", "Anonymized");
        List<Map<String, Object>> exchanges = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/contacts/" + jean + "/interactions", null,
            Map.class).getBody().get("interactions");
        assertThat(exchanges).singleElement().satisfies(exchange -> assertThat(exchange).containsEntry("summary", null)
            .containsEntry("channel", "EMAIL"));
        assertThat(jdbcTemplate.queryForObject("SELECT note FROM contact_relations WHERE first_contact_id = ? OR second_contact_id = ?",
            String.class, jean, jean)).isNull();
        for (long kept : List.of(paul, active)) {
            assertThat(call(alice, HttpMethod.GET, "/contacts/" + kept, null, Map.class).getBody()).containsEntry("anonymizedAt", null)
                .containsEntry("notes", "Some note");
        }
        assertThat(call(bob, HttpMethod.GET, "/contacts/" + bobs, null, Map.class).getBody()).containsEntry("firstName", "Bobs");
        List<Map<String, Object>> events = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/activity", null, Map.class)
            .getBody().get("events");
        assertThat(events).anySatisfy(event -> assertThat(event).containsEntry("type", "CONTACTS_ANONYMIZED").containsEntry("detail", "2"));

        // Anonymized once: the next night finds nothing more.
        assertThat(retention.anonymizeDormant(LocalDateTime.now())).isZero();
    }
}
