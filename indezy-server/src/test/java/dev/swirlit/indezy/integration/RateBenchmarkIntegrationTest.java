package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The network's rate benchmark over HTTP: the won missions' rates of the accounts that opted in, one per freelancer,
 * shown only once five freelancers back a figure.
 */
class RateBenchmarkIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    private void join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
    }

    /** A freelancer who won a mission naming the skill at that rate, in Paris or remotely, and shares it or not. */
    private Account freelancer(String skill, int rate, boolean remote, boolean shares) {
        Account account = register("rate");
        long client = idOf(call(account, HttpMethod.POST, "/clients", Map.of("companyName", "Client " + rate, "city", "Paris",
            "address", "10 rue de la Paix 75002 Paris", "isFinal", true, "freelanceId", account.freelanceId()), Map.class));
        call(account, HttpMethod.POST, "/projects", Map.of("role", "Developer", "dailyRate", rate, "status", "WON", "clientId", client,
            "techStack", skill + ", SQL", "workMode", remote ? "REMOTE" : "ONSITE", "startDate", LocalDate.now().minusMonths(2).toString(),
            "freelanceId", account.freelanceId()), Map.class);
        // An older, lower rate of the same freelancer does not count: only their latest mission naming the skill.
        call(account, HttpMethod.POST, "/projects", Map.of("role", "Developer", "dailyRate", 100, "status", "WON", "clientId", client,
            "techStack", skill, "startDate", LocalDate.now().minusMonths(6).toString(), "freelanceId", account.freelanceId()), Map.class);
        if (shares) {
            assertThat(call(account, HttpMethod.PUT, "/users/preferences", Map.of("shareRates", true), Map.class).getBody())
                .containsEntry("shareRates", true);
        }
        return account;
    }

    @Test
    @SuppressWarnings("unchecked")
    void showsAFigureOnceFiveFreelancersShareIt() {
        String skill = "Elixir" + UUID.randomUUID().toString().substring(0, 6);
        join(alice);
        assertThat(call(bob, HttpMethod.GET, "/network/benchmark?skill=" + skill, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody()).containsEntry("shareRates", false);

        freelancer(skill, 500, false, true);
        freelancer(skill, 600, false, true);
        freelancer(skill, 650, true, true);
        freelancer(skill, 700, false, true);
        freelancer(skill, 900, false, false);
        String path = "/network/benchmark?skill=" + skill.toLowerCase();
        assertThat(call(alice, HttpMethod.GET, path, null, Map.class).getBody()).containsEntry("freelancers", 4).containsEntry("threshold", 5)
            .containsEntry("median", null).containsEntry("sharing", false);

        freelancer(skill, 800, false, true);
        assertThat(call(alice, HttpMethod.GET, path, null, Map.class).getBody()).containsEntry("freelancers", 5).containsEntry("median", 650)
            .containsEntry("low", 600).containsEntry("high", 700);
        // By region: four in Île-de-France, too few to show; one remote.
        assertThat(call(alice, HttpMethod.GET, path + "&region=ILE_DE_FRANCE", null, Map.class).getBody()).containsEntry("freelancers", 4)
            .containsEntry("median", null);
        assertThat(call(alice, HttpMethod.GET, path + "&region=REMOTE", null, Map.class).getBody()).containsEntry("freelancers", 1);
        assertThat(call(alice, HttpMethod.GET, path + "&region=MARS", null, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        List<Map<String, Object>> skills = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/benchmark/skills", null, Map.class)
            .getBody().get("skills");
        assertThat(skills).anySatisfy(entry -> assertThat(entry).containsEntry("skill", skill).containsEntry("freelancers", 5));
        assertThat(skills).noneSatisfy(entry -> assertThat(entry).containsEntry("skill", "Developer"));
    }
}
