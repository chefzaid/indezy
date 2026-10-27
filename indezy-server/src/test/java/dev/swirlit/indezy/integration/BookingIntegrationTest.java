package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A recruiter books a call through Alice's link, without an account: the free starts of her hours, a start taken once
 * only, an interview keeping its time busy, a cancelled call freeing it, a closed or renewed link, and too many bookings
 * from one address.
 */
class BookingIntegrationTest extends TwoAccountsIntegrationTest {

    private static final List<Map<String, Object>> WEEKDAYS = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY").stream()
        .map(day -> Map.<String, Object>of("dayOfWeek", day, "start", "09:00", "end", "12:00")).toList();

    @Test
    @SuppressWarnings("unchecked")
    void booksAFreeCallOnceAndKeepsTheLinkInHand() {
        assertThat(call(alice, HttpMethod.GET, "/users/booking-page", null, Map.class).getBody()).containsEntry("slug", null);
        Map<String, Object> settings = new java.util.HashMap<>(Map.of("enabled", true, "title", " Intro call ", "durationMinutes", 60,
            "bufferMinutes", 0, "minNoticeHours", 0, "maxDaysAhead", 14, "windows", WEEKDAYS));
        Map<String, Object> page = call(alice, HttpMethod.PUT, "/users/booking-page", settings, Map.class).getBody();
        String slug = (String) page.get("slug");
        assertThat(slug).hasSize(16);
        assertThat((Map<String, Object>) page.get("settings")).containsEntry("title", "Intro call");

        Map<String, Object> shown = exchange("/public/booking/" + slug, HttpMethod.GET, HttpEntity.EMPTY, Map.class).getBody();
        assertThat(shown).containsEntry("title", "Intro call").containsEntry("durationMinutes", 60).containsEntry("timeZone", "Europe/Paris");
        List<Map<String, Object>> days = (List<Map<String, Object>>) shown.get("days");
        assertThat(days).isNotEmpty();
        List<String> starts = (List<String>) days.get(days.size() - 1).get("starts");
        assertThat(starts).hasSizeBetween(1, 3);
        String start = starts.get(0);

        // Booked once; the same start again is refused, and it is no longer offered.
        Map<String, Object> request = Map.of("start", start, "name", " Jean Dupont ", "email", "jean@esn.example", "company", "Capgemini",
            "message", "About the Java mission");
        ResponseEntity<Map> booked = exchange("/public/booking/" + slug, HttpMethod.POST, new HttpEntity<>(request), Map.class);
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(booked.getBody()).containsEntry("startsAt", start).containsKey("freelancer");
        assertThat(exchange("/public/booking/" + slug, HttpMethod.POST, new HttpEntity<>(request), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(offered(slug)).doesNotContain(start);
        List<Map<String, Object>> calls = call(alice, HttpMethod.GET, "/bookings", null, List.class).getBody();
        assertThat(calls).singleElement().satisfies(booking -> assertThat(booking).containsEntry("name", "Jean Dupont")
            .containsEntry("company", "Capgemini").containsEntry("cancelledAt", null));
        long id = ((Number) calls.get(0).get("id")).longValue();

        // Cancelled, the time is free again; Bob cannot cancel it.
        assertThat(call(bob, HttpMethod.POST, "/bookings/" + id + "/cancel", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.POST, "/bookings/" + id + "/cancel", null, Map.class).getBody().get("cancelledAt")).isNotNull();
        assertThat(offered(slug)).contains(start);

        // An interview planned at that time keeps it busy.
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Architecte", "dailyRate", 650, "status", "INTERVIEW",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        String local = Instant.parse(start).atZone(ZoneId.of("Europe/Paris")).toLocalDateTime().toString();
        assertThat(call(alice, HttpMethod.POST, "/interview-steps", Map.of("title", "Technical interview", "status", "PLANNED",
            "projectId", project, "date", local), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(offered(slug)).doesNotContain(start);

        // Wrong hours refused; a renewed link drops the old one; a closed link shows nothing.
        settings.put("windows", List.of(Map.of("dayOfWeek", "MONDAY", "start", "12:00", "end", "09:00")));
        assertThat(call(alice, HttpMethod.PUT, "/users/booking-page", settings, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        settings.put("windows", List.of(WEEKDAYS.get(0), WEEKDAYS.get(0)));
        assertThat(call(alice, HttpMethod.PUT, "/users/booking-page", settings, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String renewed = (String) call(alice, HttpMethod.POST, "/users/booking-page/new-link", null, Map.class).getBody().get("slug");
        assertThat(renewed).isNotEqualTo(slug);
        assertThat(exchange("/public/booking/" + slug, HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        settings.put("windows", WEEKDAYS);
        settings.put("enabled", false);
        call(alice, HttpMethod.PUT, "/users/booking-page", settings, Map.class);
        assertThat(exchange("/public/booking/" + renewed, HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/users/booking-page/new-link", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void takesFiveBookingsAnHourFromOneAddress() {
        Map<String, Object> settings = Map.of("enabled", true, "title", "Call", "durationMinutes", 15, "bufferMinutes", 0, "minNoticeHours", 0,
            "maxDaysAhead", 30, "windows", WEEKDAYS);
        String slug = (String) call(bob, HttpMethod.PUT, "/users/booking-page", settings, Map.class).getBody().get("slug");
        List<String> starts = ((List<Map<String, Object>>) exchange("/public/booking/" + slug, HttpMethod.GET, HttpEntity.EMPTY, Map.class)
            .getBody().get("days")).stream().flatMap(day -> ((List<String>) day.get("starts")).stream()).toList();
        HttpHeaders proxied = new HttpHeaders();
        proxied.set("X-Forwarded-For", "198.51.100.7, 203.0.113.9");
        for (int i = 0; i < 6; i++) {
            ResponseEntity<String> answer = exchange("/public/booking/" + slug, HttpMethod.POST, new HttpEntity<>(Map.of("start", starts.get(i),
                "name", "R" + i, "email", "r" + i + "@esn.example"), proxied), String.class);
            assertThat(answer.getStatusCode()).as("booking " + i).isEqualTo(i < 5 ? HttpStatus.CREATED : HttpStatus.TOO_MANY_REQUESTS);
        }
        // Another client behind the same proxy still books.
        proxied.set("X-Forwarded-For", "203.0.113.10");
        assertThat(exchange("/public/booking/" + slug, HttpMethod.POST, new HttpEntity<>(Map.of("start", starts.get(6), "name", "Other",
            "email", "o@esn.example"), proxied), String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(exchange("/public/booking/" + slug, HttpMethod.POST, new HttpEntity<>(Map.of("start", starts.get(7), "name", "",
            "email", "not-an-email")), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @SuppressWarnings("unchecked")
    private List<String> offered(String slug) {
        return ((List<Map<String, Object>>) exchange("/public/booking/" + slug, HttpMethod.GET, HttpEntity.EMPTY, Map.class).getBody()
            .get("days")).stream().flatMap(day -> ((List<String>) day.get("starts")).stream()).toList();
    }
}
