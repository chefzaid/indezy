package dev.swirlit.indezy.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice tracks her client's purchase order over HTTP: the days of her activity reports used against its ceiling, an
 * amendment adding days, the dashboard's alert from 80 %, and Bob kept out.
 */
class PurchaseOrderIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void tracksAnOrderAndItsAmendmentAgainstTheActivityReports() throws Exception {
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Architecte", "dailyRate", 500, "status", "WON",
            "clientId", aliceClientId), Map.class));
        // Three and a half days worked last month, an absence not counted, and a day before the order.
        LocalDate monday = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)).plusWeeks(1);
        String month = "/projects/" + project + "/activity-reports/" + monday.getYear() + "/" + monday.getMonthValue();
        call(alice, HttpMethod.PUT, month, Map.of("days", List.of(day(monday.minusDays(7), "ONSITE", false), day(monday, "ONSITE", false),
            day(monday.plusDays(1), "REMOTE", false), day(monday.plusDays(2), "ONSITE", true), day(monday.plusDays(3), "REMOTE", false),
            day(monday.plusDays(4), "ABSENCE", false))), Map.class);
        String orders = "/projects/" + project + "/purchase-orders";

        Map<String, Object> order = new HashMap<>(Map.of("number", " BC-2026-118 ", "periodStart", monday.minusDays(1).toString(),
            "dayCeiling", 4, "notes", " Signed by procurement "));
        Map<String, Object> created = call(alice, HttpMethod.POST, orders, order, Map.class).getBody();
        long id = ((Number) created.get("id")).longValue();
        assertThat(created).containsEntry("number", "BC-2026-118").containsEntry("notes", "Signed by procurement")
            .containsEntry("projectId", (int) project);
        assertThat((Map<String, Object>) created.get("usage")).containsEntry("workedDays", 3.5).containsEntry("amount", 1750.0)
            .containsEntry("dayCeiling", 4.0).containsEntry("share", 0.875).containsEntry("daysLeft", 0.5).containsEntry("level", "NEAR")
            .containsEntry("periodEnd", null);
        assertThat(alerts()).singleElement().satisfies(alert -> assertThat(alert).containsEntry("orderId", (int) id)
            .containsEntry("projectRole", "Architecte").containsEntry("number", "BC-2026-118").containsEntry("percentUsed", 87)
            .containsEntry("daysLeft", 0.5).containsEntry("exceeded", false));

        // An amendment adds days and an end: the order is no longer near its ceiling.
        Map<String, Object> amendment = new HashMap<>(Map.of("amendsId", id, "number", "Avenant 1", "dayCeiling", 10,
            "periodEnd", monday.plusMonths(3).toString()));
        Map<String, Object> amended = call(alice, HttpMethod.POST, orders, amendment, Map.class).getBody();
        assertThat(amended).containsEntry("id", (int) id);
        assertThat((List<Map<String, Object>>) amended.get("amendments")).singleElement().satisfies(item ->
            assertThat(item).containsEntry("number", "Avenant 1").containsEntry("dayCeiling", 10.0));
        assertThat((Map<String, Object>) amended.get("usage")).containsEntry("dayCeiling", 14.0).containsEntry("level", "OK")
            .containsEntry("periodEnd", monday.plusMonths(3).toString());
        assertThat(alerts()).isEmpty();

        // A ceiling in euros only counts the days at the daily rate; over it, the alert says so.
        long amendmentId = ((Number) ((List<Map<String, Object>>) amended.get("amendments")).get(0).get("id")).longValue();
        call(alice, HttpMethod.DELETE, "/purchase-orders/" + amendmentId, null, Void.class);
        order.remove("dayCeiling");
        order.put("amountCeiling", 1500);
        Map<String, Object> inEuros = call(alice, HttpMethod.PUT, "/purchase-orders/" + id, order, Map.class).getBody();
        assertThat((List<?>) inEuros.get("amendments")).isEmpty();
        assertThat((Map<String, Object>) inEuros.get("usage")).containsEntry("level", "EXCEEDED").containsEntry("daysLeft", -0.5);
        assertThat(alerts()).singleElement().satisfies(alert -> assertThat(alert).containsEntry("exceeded", true)
            .containsEntry("percentUsed", 116));

        // Refused: no start, no ceiling, an end before the start, an amendment adding nothing or of another mission's order.
        for (Map.Entry<String, Object> wrong : Map.<String, Object>of("periodStart", "", "amountCeiling", "", "periodEnd",
            monday.minusMonths(2).toString()).entrySet()) {
            Map<String, Object> request = new HashMap<>(order);
            if ("".equals(wrong.getValue())) {
                request.remove(wrong.getKey());
            } else {
                request.put(wrong.getKey(), wrong.getValue());
            }
            assertThat(call(alice, HttpMethod.POST, orders, request, Map.class).getBody()).as(wrong.getKey())
                .containsKey("validationErrors");
        }
        assertThat(call(alice, HttpMethod.POST, orders, Map.of("amendsId", id, "number", "Avenant 2"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        long other = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Other", "dailyRate", 400, "status", "WON", "clientId", aliceClientId),
            Map.class));
        assertThat(call(alice, HttpMethod.POST, "/projects/" + other + "/purchase-orders", Map.of("amendsId", id, "number", "X",
            "dayCeiling", 1), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Bob reads, changes and deletes none of it, nor writes on Alice's mission.
        assertThat(call(bob, HttpMethod.GET, orders, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, orders, order, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.PUT, "/purchase-orders/" + id, order, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/purchase-orders/" + id, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // In the account's data export; deleted with the order's amendments, and with the mission.
        String export = call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody();
        assertThat((List<?>) new ObjectMapper().readValue(export, Map.class).get("purchaseOrders")).hasSize(1);
        call(alice, HttpMethod.POST, orders, Map.of("amendsId", id, "number", "Avenant 3", "dayCeiling", 2), Map.class);
        assertThat(call(alice, HttpMethod.DELETE, "/purchase-orders/" + id, null, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat((List<?>) call(alice, HttpMethod.GET, orders, null, List.class).getBody()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchase_orders", Integer.class)).isZero();
        call(alice, HttpMethod.POST, orders, order, Map.class);
        call(alice, HttpMethod.DELETE, "/projects/" + project, null, Void.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchase_orders", Integer.class)).isZero();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> alerts() {
        return (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/projects/stats/dashboard/" + alice.freelanceId(), null, Map.class)
            .getBody().get("purchaseOrderAlerts");
    }

    private static Map<String, Object> day(LocalDate date, String type, boolean halfDay) {
        return Map.of("date", date.toString(), "type", type, "halfDay", halfDay);
    }
}
