package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ESN scorecards across members over HTTP: each freelancer's record of an ESN once, the members who turned it off
 * left out, a figure shown only once five freelancers back it, the ESN named.
 */
class EsnScorecardIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    private void join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
    }

    /** A freelancer who won a mission through the ESN, the client paying it {@code clientRate}, and shares it or not. */
    private void freelancer(String esnName, int clientRate, boolean shares) {
        Account account = register("esn");
        long esn = idOf(call(account, HttpMethod.POST, "/clients", Map.of("companyName", esnName, "city", "Paris", "isFinal", false,
            "freelanceId", account.freelanceId()), Map.class));
        long bank = idOf(call(account, HttpMethod.POST, "/clients", Map.of("companyName", "Banque " + clientRate, "city", "Paris", "isFinal", true,
            "freelanceId", account.freelanceId()), Map.class));
        call(account, HttpMethod.POST, "/projects", Map.of("role", "Developer", "dailyRate", 600, "clientDailyRate", clientRate, "status", "WON",
            "clientId", bank, "middlemanId", esn, "freelanceId", account.freelanceId()), Map.class);
        if (!shares) {
            assertThat(call(account, HttpMethod.PUT, "/users/preferences", Map.of("shareEsnRecords", false), Map.class).getBody())
                .containsEntry("shareEsnRecords", false);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void namesAnEsnOnceFiveFreelancersBackItsFigures() {
        String esnName = "Conseil " + UUID.randomUUID().toString().substring(0, 6);
        join(alice);
        assertThat(call(bob, HttpMethod.GET, "/network/esn-scorecards", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody()).containsEntry("shareEsnRecords", true);
        // Alice knows the ESN but had no opportunity with it: her record does not count.
        long aliceEsn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", esnName.toUpperCase(), "city", "Lyon",
            "isFinal", false, "freelanceId", alice.freelanceId()), Map.class));

        freelancer(esnName, 750, true);
        freelancer(esnName, 800, true);
        freelancer(esnName, 700, true);
        freelancer(esnName, 1000, true);
        freelancer(esnName, 650, false);
        String path = "/clients/" + aliceEsn + "/scorecard/members";
        assertThat(call(alice, HttpMethod.GET, path, null, Map.class).getBody()).containsEntry("freelancers", 4).containsEntry("threshold", 5)
            .containsEntry("offerRate", null).containsEntry("sharing", true);
        List<Map<String, Object>> companies = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/esn-scorecards", null, Map.class)
            .getBody().get("companies");
        assertThat(companies).noneSatisfy(company -> assertThat(company).containsEntry("company", esnName));

        freelancer(esnName, 750, true);
        // Margins 20, 25, 14, 40 and 20 %: the middle half from 20 to 25.
        assertThat(call(alice, HttpMethod.GET, path, null, Map.class).getBody()).containsEntry("company", esnName).containsEntry("freelancers", 5)
            .containsEntry("offerRate", 100).containsEntry("marginLow", 20).containsEntry("marginHigh", 25).containsEntry("processDays", null);
        companies = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/esn-scorecards", null, Map.class).getBody().get("companies");
        assertThat(companies).anySatisfy(company -> assertThat(company).containsEntry("company", esnName).containsEntry("freelancers", 5));
        assertThat(call(bob, HttpMethod.GET, path, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
