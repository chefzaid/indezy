package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.NetworkMeetupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Events over HTTP: Alice organizes a meetup and a webinar, Bob finds them by tag and answers, reads who goes and adds one
 * to his calendar; he is reminded the day before, and told when Alice cancels it.
 */
class NetworkEventsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private NetworkMeetupService meetups;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private void join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
    }

    private static String in(long hours) {
        return LocalDateTime.now(ZoneId.of("Europe/Paris")).plusHours(hours).truncatedTo(ChronoUnit.MINUTES).toString();
    }

    private static Map<String, Object> event(String title, String startsAt, boolean online, String where, List<String> tags) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("startsAt", startsAt);
        body.put("online", online);
        body.put(online ? "link" : "place", where);
        body.put("tags", tags);
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<Object> ids(Account reader, String query) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/events" + query, null, Map.class).getBody().get("meetups"))
            .stream().map(meetup -> meetup.get("id")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void organizesAnswersRemindsAndCancelsEvents() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        String tag = "kafka" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        assertThat(call(alice, HttpMethod.POST, "/network/events", event("Trop tôt", in(-2), false, "Lyon", List.of()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/network/events", event("Sans lien", in(48), true, "", List.of()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> created = call(alice, HttpMethod.POST, "/network/events",
            event("Apéro Kafka, Lyon", in(10), false, "Le Comptoir, 12 rue Mercière, Lyon", List.of("#" + tag.toUpperCase(), "lyon")), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("going", 1).containsEntry("mine", "GOING").containsEntry("own", true)
            .containsEntry("editable", true).containsEntry("tags", List.of(tag, "lyon"));
        Object meetup = created.getBody().get("id");
        Object webinar = call(alice, HttpMethod.POST, "/network/events", event("Webinar Flink", in(72), true, "https://meet.example.org/flink",
            List.of(tag)), Map.class).getBody().get("id");

        // Bob finds both by tag, the soonest first, and answers.
        assertThat(ids(bob, "?tag=" + tag)).containsExactly(meetup, webinar);
        assertThat(ids(bob, "?tag=" + tag + "&q=flink")).containsExactly(webinar);
        Map<String, Object> answered = call(bob, HttpMethod.PUT, "/network/events/" + meetup + "/attendance?status=GOING", null, Map.class).getBody();
        assertThat(answered).containsEntry("going", 2).containsEntry("mine", "GOING").containsEntry("own", false).containsEntry("editable", false);
        call(bob, HttpMethod.PUT, "/network/events/" + webinar + "/attendance?status=INTERESTED", null, Map.class);
        assertThat(ids(bob, "?scope=ATTENDING&tag=" + tag)).containsExactly(meetup, webinar);
        assertThat((List<Object>) call(bob, HttpMethod.GET, "/network/events/" + meetup + "/attendees", null, Map.class).getBody().get("going"))
            .hasSize(2);
        assertThat(call(bob, HttpMethod.PUT, "/network/events/" + meetup, event("Autre", in(20), false, "Lyon", List.of()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // His calendar takes it as an .ics file, its time in UTC.
        ResponseEntity<String> ics = call(bob, HttpMethod.GET, "/network/events/" + meetup + "/ics", null, String.class);
        assertThat(ics.getHeaders().getContentType().toString()).startsWith("text/calendar");
        assertThat(ics.getBody()).contains("BEGIN:VEVENT").contains("SUMMARY:Apéro Kafka\\, Lyon").contains("LOCATION:Le Comptoir\\, 12 rue Mercière\\, Lyon")
            .containsPattern("DTSTART:\\d{8}T\\d{6}Z").contains("\r\n");

        // The day before, Bob and Alice are reminded, once.
        assertThat(meetups.remindDue()).isGreaterThanOrEqualTo(2);
        assertThat(meetups.remindDue()).isZero();
        List<Map<String, Object>> notices = call(bob, HttpMethod.GET, "/network/notifications", null, List.class).getBody();
        assertThat(notices).anySatisfy(notice -> {
            assertThat(notice).containsEntry("type", "MEETUP_REMINDER");
            assertThat((Map<String, Object>) notice.get("meetup")).containsEntry("id", meetup).containsEntry("title", "Apéro Kafka, Lyon");
        });

        // Alice cancels it: Bob is told, and it takes no more answers.
        assertThat(call(alice, HttpMethod.DELETE, "/network/events/" + meetup, null, Map.class).getBody()).containsEntry("cancelled", true);
        assertThat(call(bob, HttpMethod.GET, "/network/notifications", null, List.class).getBody())
            .anySatisfy(notice -> assertThat((Map<String, Object>) notice).containsEntry("type", "MEETUP_CANCELLED"));
        assertThat(ids(bob, "?tag=" + tag)).containsExactly(webinar);
        assertThat(call(bob, HttpMethod.PUT, "/network/events/" + meetup + "/attendance?status=GOING", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        call(bob, HttpMethod.DELETE, "/network/events/" + webinar + "/attendance", null, Map.class);
        assertThat(ids(bob, "?scope=ATTENDING&tag=" + tag)).isEmpty();
        assertThat(ids(alice, "?scope=ORGANIZED&tag=" + tag)).containsExactly(webinar, meetup);
    }
}
