package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice keeps her past positions and schools; Bob sees and changes none of them; her data export carries them and her
 * public profile shows only the ones she chose.
 */
class CareerIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void keepsThePastPositionsAndSchools() {
        Map<String, Object> bank = new HashMap<>(Map.of("employer", "Banque Exemple", "title", "Développeuse Java", "startsOn", "2015-09-01",
            "endsOn", "2019-06-30", "stack", "Java 8", "shownPublicly", true));
        Map<String, Object> created = call(alice, HttpMethod.POST, "/users/experiences", bank, Map.class).getBody();
        assertThat(created).containsEntry("employer", "Banque Exemple").containsEntry("description", null);
        String position = "/users/experiences/" + created.get("id");
        call(alice, HttpMethod.POST, "/users/experiences", Map.of("employer", "Alice Conseil", "title", "Lead Java", "startsOn", "2024-01-01",
            "shownPublicly", false), Map.class);
        // The running position comes first.
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/experiences", null, List.class).getBody())
            .extracting(entry -> entry.get("title")).containsExactly("Lead Java", "Développeuse Java");

        bank.put("description", "Paie et virements.");
        assertThat(call(alice, HttpMethod.PUT, position, bank, Map.class).getBody()).containsEntry("description", "Paie et virements.");
        bank.put("endsOn", "2010-01-01");
        assertThat(call(alice, HttpMethod.PUT, position, bank, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/users/experiences", Map.of("employer", " ", "title", "Dev"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> school = call(alice, HttpMethod.POST, "/users/educations", Map.of("school", "INSA Lyon", "degree", "Ingénieure",
            "startYear", 2010, "endYear", 2015, "shownPublicly", true), Map.class).getBody();
        call(alice, HttpMethod.POST, "/users/educations", Map.of("school", "Lycée du Parc", "endYear", 2010), Map.class);
        assertThat(call(alice, HttpMethod.POST, "/users/educations", Map.of("school", "X", "startYear", 2015, "endYear", 2012), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String education = "/users/educations/" + school.get("id");

        assertThat(call(bob, HttpMethod.GET, "/users/experiences", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/users/educations", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.DELETE, position, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.PUT, education, Map.of("school", "Y"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("Banque Exemple", "Lycée du Parc");

        call(alice, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", true, "slug", "alice-career"), Map.class);
        Map<String, Object> page = exchange("/public/profiles/alice-career", HttpMethod.GET, HttpEntity.EMPTY, Map.class).getBody();
        assertThat((List<Map<String, Object>>) page.get("experiences")).extracting(entry -> entry.get("employer")).containsExactly("Banque Exemple");
        assertThat((List<Map<String, Object>>) page.get("educations")).extracting(entry -> entry.get("school")).containsExactly("INSA Lyon");

        assertThat(call(alice, HttpMethod.DELETE, position, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.DELETE, education, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/users/educations", null, List.class).getBody()).hasSize(1);
    }
}
