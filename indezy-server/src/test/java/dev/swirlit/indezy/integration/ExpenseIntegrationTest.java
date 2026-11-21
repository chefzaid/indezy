package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A workspace's expenses and what they change in its net income by its status, over HTTP. */
class ExpenseIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void recordsExpensesAndCountsThemAsTheStatusDoes() {
        long mission = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java lead", "dailyRate", 600, "status", "WON",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        Map<String, Object> train = new HashMap<>(Map.of("spentOn", "2026-03-12", "label", " Train to Lyon ", "category", "TRAVEL",
            "amount", 120, "vat", 20, "projectId", mission));
        Map<String, Object> laptop = Map.of("spentOn", "2026-03-02", "label", "Laptop", "category", "EQUIPMENT", "amount", 1200, "vat", 200);
        Map<String, Object> created = call(alice, HttpMethod.POST, "/expenses", train, Map.class).getBody();
        long trainId = ((Number) created.get("id")).longValue();
        assertThat(created).containsEntry("label", "Train to Lyon").containsEntry("amount", 120.0).containsEntry("projectRole", "Java lead");
        call(alice, HttpMethod.POST, "/expenses", laptop, Map.class);
        call(alice, HttpMethod.POST, "/expenses", Map.of("spentOn", "2025-12-30", "label", "Book", "category", "TRAINING", "amount", 40), Map.class);

        // More VAT than the amount, or a mission of another workspace, is refused; Bob sees nothing of Alice's.
        Map<String, Object> wrong = new HashMap<>(train);
        wrong.put("vat", 500);
        assertThat(call(alice, HttpMethod.POST, "/expenses", wrong, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        long bobClient = idOf(call(bob, HttpMethod.POST, "/clients", Map.of("companyName", "Bob Corp", "city", "Lyon", "isFinal", true,
            "freelanceId", bob.freelanceId()), Map.class));
        long bobMission = idOf(call(bob, HttpMethod.POST, "/projects", Map.of("role", "Audit", "dailyRate", 500, "status", "WON",
            "clientId", bobClient, "freelanceId", bob.freelanceId()), Map.class));
        wrong.put("vat", 20);
        wrong.put("projectId", bobMission);
        assertThat(call(alice, HttpMethod.POST, "/expenses", wrong, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/expenses", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.PUT, "/expenses/" + trainId, train, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/expenses/" + trainId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // The year's list, the latest first.
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/expenses?year=2026", null, List.class).getBody())
            .extracting(expense -> expense.get("label")).containsExactly("Train to Lyon", "Laptop");
        assertThat(call(alice, HttpMethod.GET, "/expenses", null, List.class).getBody()).hasSize(3);

        // No status yet: only recorded, at their full cost.
        Map<String, Object> summary = call(alice, HttpMethod.GET, "/expenses/summary/2026", null, Map.class).getBody();
        assertThat(summary).containsEntry("treatment", "RECORDED").containsEntry("total", 1320.0).containsEntry("vat", 220.0)
            .containsEntry("deductible", 0.0).containsEntry("netCost", 1320.0).containsEntry("reversionRate", 55.0);
        assertThat((List<Number>) summary.get("months")).hasSize(12).element(2).isEqualTo(1320.0);
        assertThat((Map<String, Object>) summary.get("byCategory")).containsEntry("TRAVEL", 120.0).containsEntry("EQUIPMENT", 1200.0);

        // A micro-entreprise: not deductible, the VAT not recovered.
        Map<String, Object> billing = new HashMap<>(Map.of("legalName", "Alice Martin", "legalForm", "MICRO_ENTREPRISE", "vatExempt", true));
        call(alice, HttpMethod.PUT, "/users/billing", billing, Map.class);
        assertThat(call(alice, HttpMethod.GET, "/expenses/summary/2026", null, Map.class).getBody()).containsEntry("treatment", "FLAT_ALLOWANCE")
            .containsEntry("deductible", 0.0).containsEntry("netCost", 1320.0);

        // A SASU charging VAT: the VAT recovered, the rest lowers the profit, costing the reversion rate's share.
        jdbcTemplate.update("UPDATE freelances SET reversion_share = 60 WHERE id = ?", alice.freelanceId());
        billing.put("legalForm", "SASU");
        billing.put("vatExempt", false);
        call(alice, HttpMethod.PUT, "/users/billing", billing, Map.class);
        assertThat(call(alice, HttpMethod.GET, "/expenses/summary/2026", null, Map.class).getBody()).containsEntry("treatment", "DEDUCTIBLE")
            .containsEntry("deductible", 1100.0).containsEntry("netCost", 660.0).containsEntry("reversionRate", 60.0);

        // In portage, reimbursed out of the turnover before charges.
        billing.put("legalForm", "PORTAGE");
        billing.put("vatExempt", true);
        call(alice, HttpMethod.PUT, "/users/billing", billing, Map.class);
        assertThat(call(alice, HttpMethod.GET, "/expenses/summary/2026", null, Map.class).getBody()).containsEntry("treatment", "REIMBURSED")
            .containsEntry("deductible", 1320.0).containsEntry("netCost", 792.0);

        // Changed, then deleted; the mission's deletion keeps the expense.
        train.put("amount", 150);
        train.put("projectId", null);
        assertThat(call(alice, HttpMethod.PUT, "/expenses/" + trainId, train, Map.class).getBody()).containsEntry("amount", 150.0)
            .containsEntry("projectId", null);
        call(alice, HttpMethod.DELETE, "/projects/" + mission, null, String.class);
        assertThat(call(alice, HttpMethod.DELETE, "/expenses/" + trainId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/expenses?year=2026", null, List.class).getBody()).hasSize(1);
    }
}
