package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** An opportunity's commute, over HTTP: both ways there for its owner, nothing for another account. */
class ProjectCommuteIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void answersBothWaysThereOnlyToTheOpportunitysOwner() {
        long projectId = ((Number) call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650,
            "clientId", aliceClientId, "workMode", "ONSITE"), Map.class).getBody().get("id")).longValue();

        List<Map<String, Object>> commutes = call(alice, HttpMethod.GET, "/commute/project/" + projectId, null, List.class).getBody();
        assertThat(commutes).extracting(commute -> commute.get("travelMode")).containsExactly("DRIVING", "TRANSIT");
        assertThat(commutes).allSatisfy(commute -> assertThat(commute).containsEntry("onsiteDaysPerMonth", 20)
            .containsEntry("remote", false));

        assertThat(call(bob, HttpMethod.GET, "/commute/project/" + projectId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void keepsTheLongestCommutesOfTheProfile() {
        Map profile = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("maxDriveMinutes", 601);
        assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        profile.put("maxDriveMinutes", 45);
        profile.put("maxTransitMinutes", 60);
        call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class);
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody())
            .containsEntry("maxDriveMinutes", 45).containsEntry("maxTransitMinutes", 60);
        assertThat(call(bob, HttpMethod.GET, "/users/profile", null, Map.class).getBody().get("maxDriveMinutes")).isNull();
    }

    @Test
    void keepsWhatTheCommuteCosts() {
        Map profile = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("kmRate", 6);
        assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        profile.put("kmRate", 0.6);
        profile.put("transitPassMonthly", 88.8);
        call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class);
        Map saved = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        assertThat(((Number) saved.get("kmRate")).doubleValue()).isEqualTo(0.6);
        assertThat(((Number) saved.get("transitPassMonthly")).doubleValue()).isEqualTo(88.8);
    }

    @Test
    void exportsTheYearsKilometresToTheirOwnerOnly() {
        org.springframework.http.ResponseEntity<String> csv = call(alice, HttpMethod.GET,
            "/commute/kilometres/" + alice.freelanceId() + "/2026?language=EN", null, String.class);
        assertThat(csv.getHeaders().getContentDisposition().getFilename()).isEqualTo("kilometres-2026.csv");
        assertThat(csv.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        assertThat(csv.getBody()).startsWith("Month;Client;Mission;Days on site;Round trip (km);Kilometres").contains("Total;;;;;0");
        assertThat(call(alice, HttpMethod.GET, "/commute/kilometres/" + alice.freelanceId() + "/1999", null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.GET, "/commute/kilometres/" + alice.freelanceId() + "/2026", null, String.class)
            .getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTheOpportunitiesOfTheirOwnerOnly() {
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "clientId", aliceClientId), Map.class);
        // The test profile locates no address: the opportunity is counted, not placed.
        Map<String, Object> map = call(alice, HttpMethod.GET, "/commute/map/" + alice.freelanceId(), null, Map.class).getBody();
        assertThat(map).containsEntry("unplaced", 1).containsEntry("pins", List.of());
        assertThat(call(bob, HttpMethod.GET, "/commute/map/" + alice.freelanceId(), null, String.class).getStatusCode().is4xxClientError())
            .isTrue();
    }
}
