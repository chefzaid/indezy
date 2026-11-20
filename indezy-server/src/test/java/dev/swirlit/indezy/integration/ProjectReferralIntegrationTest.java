package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Co-optation over HTTP: Alice records who referred her mission and the bonus she pays per day worked, counted from her
 * timesheet; a share of what it earns, then paid; another mission she referred someone to brings her a fixed bonus, and
 * the summary tells what is still to pay and to receive. Bob reads none of it.
 */
class ProjectReferralIntegrationTest extends TwoAccountsIntegrationTest {

    private long mission(String role) {
        return idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", role, "dailyRate", 600, "status", "WON", "clientId", aliceClientId,
            "freelanceId", alice.freelanceId(), "startDate", "2026-09-01", "durationInMonths", 6), Map.class));
    }

    private static Map<String, Object> referral(String direction, String kind, Object amount, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("referrer", "@leo_m");
        body.put("direction", direction);
        body.put("bonusKind", kind);
        body.put("bonusAmount", amount);
        body.put("status", status);
        return body;
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsTheBonusFromTheTimesheet() {
        long project = mission("Lead Kafka");
        assertThat(call(alice, HttpMethod.GET, "/projects/" + project + "/referral", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        call(alice, HttpMethod.PUT, "/projects/" + project + "/activity-reports/2026/11", Map.of("days", List.of(
            Map.of("date", "2026-11-02", "type", "ONSITE", "halfDay", false), Map.of("date", "2026-11-03", "type", "REMOTE", "halfDay", false),
            Map.of("date", "2026-11-04", "type", "REMOTE", "halfDay", true))), Map.class);

        Map<String, Object> perDay = call(alice, HttpMethod.PUT, "/projects/" + project + "/referral", referral("I_PAY", "PER_DAY", 20, "DUE"),
            Map.class).getBody();
        assertThat(perDay).containsEntry("workedDays", 2.5).containsEntry("earned", 1500.0).containsEntry("bonus", 50.0);
        Map<String, Object> share = call(alice, HttpMethod.PUT, "/projects/" + project + "/referral", referral("I_PAY", "PERCENT", 5, "DUE"),
            Map.class).getBody();
        assertThat(share).containsEntry("bonus", 75.0);
        assertThat((Map<String, Object>) share.get("referral")).containsEntry("referrer", "@leo_m").containsEntry("paidOn", null);
        assertThat(call(alice, HttpMethod.PUT, "/projects/" + project + "/referral", referral("I_PAY", "PERCENT", 150, "DUE"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/projects/" + project + "/referral", referral("I_PAY", "FIXED", null, "DUE"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        long referred = mission("Dev Spark");
        call(alice, HttpMethod.PUT, "/projects/" + referred + "/referral", referral("I_RECEIVE", "FIXED", 500, "PROMISED"), Map.class);
        Map<String, Object> summary = call(alice, HttpMethod.GET, "/projects/referrals/" + alice.freelanceId(), null, Map.class).getBody();
        assertThat((List<Object>) summary.get("referrals")).hasSize(2);
        assertThat(summary).containsEntry("toPay", 75.0).containsEntry("toReceive", 500.0);

        // Paid: nothing more to pay; Bob reads none of it.
        Map<String, Object> paid = referral("I_PAY", "PERCENT", 5, "PAID");
        paid.put("paidOn", "2026-12-05");
        assertThat((Map<String, Object>) call(alice, HttpMethod.PUT, "/projects/" + project + "/referral", paid, Map.class).getBody().get("referral"))
            .containsEntry("paidOn", "2026-12-05");
        assertThat(call(alice, HttpMethod.GET, "/projects/referrals/" + alice.freelanceId(), null, Map.class).getBody()).containsEntry("toPay", 0);
        assertThat(call(bob, HttpMethod.GET, "/projects/" + project + "/referral", null, String.class).getStatusCode().is4xxClientError()).isTrue();
        assertThat(call(alice, HttpMethod.DELETE, "/projects/" + project + "/referral", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.DELETE, "/projects/" + referred, null, String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(call(alice, HttpMethod.GET, "/projects/referrals/" + alice.freelanceId(), null, Map.class).getBody().get("referrals"))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }
}
