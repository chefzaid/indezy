package dev.swirlit.indezy.integration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice invoices the month her ESN validated, over HTTP: a draft of the report, issued once her billing identity and the
 * client's address are there, numbered without gaps, frozen once issued, paid, and out of Bob's reach.
 */
class InvoiceIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void invoicesAValidatedMonthThenNumbersFreezesAndSettlesIt() throws IOException {
        long esn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Capgemini", "city", "Paris", "isFinal", false,
            "freelanceId", alice.freelanceId(), "activityReportLanguage", "EN"), Map.class));
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Développeur Java", "dailyRate", 600,
            "status", "WON", "clientId", aliceClientId, "middlemanId", esn, "startDate", "2026-09-01", "durationInMonths", 6), Map.class));
        String reports = "/projects/" + project + "/activity-reports/2026/11";
        call(alice, HttpMethod.PUT, reports, Map.of("days", List.of(Map.of("date", "2026-11-02", "type", "ONSITE", "halfDay", false),
            Map.of("date", "2026-11-03", "type", "REMOTE", "halfDay", false), Map.of("date", "2026-11-04", "type", "ONSITE", "halfDay", true))),
            Map.class);
        Map<String, Object> fromReport = Map.of("projectId", project, "year", 2026, "month", 11);
        assertThat(call(alice, HttpMethod.POST, "/invoices/from-report", fromReport, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        String token = (String) call(alice, HttpMethod.POST, reports + "/submit", null, Map.class).getBody().get("token");
        HttpHeaders link = new HttpHeaders();
        link.set("X-Report-Token", token);
        link.setContentType(MediaType.APPLICATION_JSON);
        exchange("/public/activity-reports/decision", HttpMethod.POST, new HttpEntity<>(Map.of("approved", true, "validatorName",
            "Jean Dupont"), link), Map.class);

        // A draft of the validated month, billed to the ESN in its language; one per report, out of Bob's reach.
        assertThat(call(bob, HttpMethod.POST, "/invoices/from-report", fromReport, String.class).getStatusCode())
            .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        Map<String, Object> draft = call(alice, HttpMethod.POST, "/invoices/from-report", fromReport, Map.class).getBody();
        long first = ((Number) draft.get("id")).longValue();
        assertThat(draft).containsEntry("status", "DRAFT").containsEntry("number", null).containsEntry("clientId", (int) esn)
            .containsEntry("language", "EN").containsEntry("totalExcludingVat", 1500.0).containsEntry("total", 1500.0);
        assertThat((List<Map<String, Object>>) draft.get("lines")).singleElement().satisfies(line -> assertThat(line)
            .containsEntry("description", "Services: Développeur Java — November 2026").containsEntry("quantity", 2.5)
            .containsEntry("unitPrice", 600.0).containsEntry("vatRate", 0.0));
        assertThat(call(alice, HttpMethod.POST, "/invoices/from-report", fromReport, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(pdf(alice, first)).contains("DRAFT — this document is not an invoice");

        // Issued only with a complete billing identity and the client's address.
        assertThat(call(alice, HttpMethod.POST, "/invoices/" + first + "/issue", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> billing = new HashMap<>(Map.of("legalName", "Alice Martin", "legalForm", "MICRO_ENTREPRISE",
            "siret", "55210055400012", "vatExempt", true, "address", "18 rue du Sentier\n75002 Paris", "iban", "FR7630006000011234567890189"));
        call(alice, HttpMethod.PUT, "/users/billing", billing, Map.class);
        assertThat(call(alice, HttpMethod.POST, "/invoices/" + first + "/issue", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> client = call(alice, HttpMethod.GET, "/clients/" + esn, null, Map.class).getBody();
        client.put("address", "145 quai du Président Roosevelt");
        client.put("siren", "330703844");
        call(alice, HttpMethod.PUT, "/clients/" + esn, client, Map.class);
        assertThat(call(alice, HttpMethod.POST, "/invoices/" + first + "/issue", Map.of("issueDate", LocalDate.now().plusDays(2).toString()),
            String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> issued = call(alice, HttpMethod.POST, "/invoices/" + first + "/issue", Map.of("issueDate", "2026-09-01"),
            Map.class).getBody();
        assertThat(issued).containsEntry("status", "ISSUED").containsEntry("number", "F2026-0001").containsEntry("issueDate", "2026-09-01")
            .containsEntry("dueDate", "2026-10-01").containsEntry("overdue", true);

        // Frozen: no change, no deletion, and later identity changes leave its PDF as it was.
        assertThat(call(alice, HttpMethod.DELETE, "/invoices/" + first, null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        billing.put("legalName", "Alice Martin Conseil");
        call(alice, HttpMethod.PUT, "/users/billing", billing, Map.class);
        String text = pdf(alice, first);
        assertThat(text).contains("Invoice", "No. F2026-0001", "Alice Martin EI", "SIRET: 55210055400012", "Capgemini",
            "145 quai du Président Roosevelt", "SIREN: 330703844", "Services: Développeur Java — November 2026", "€1,500.00",
            "Amount due", "VAT not applicable, art. 293 B", "Payment within 30 days, by 1 October 2026",
            "IBAN: FR76 3000 6000 0112 3456 7890 189", "No discount for early payment", "fixed recovery fee of", "€40 (art. L441-10")
            .doesNotContain("Alice Martin Conseil", "DRAFT");

        // A draft by hand in French, numbered next, without a gap.
        Map<String, Object> written = call(alice, HttpMethod.POST, "/invoices", Map.of("clientId", aliceClientId, "language", "FR",
            "lines", List.of(Map.of("description", "Audit", "quantity", 1, "unitPrice", 1200.5, "vatRate", 0),
                Map.of("description", "Formation", "quantity", 0.5, "unitPrice", 800, "vatRate", 0))), Map.class).getBody();
        long second = ((Number) written.get("id")).longValue();
        assertThat(written).containsEntry("totalExcludingVat", 1600.5);
        assertThat(call(alice, HttpMethod.PUT, "/invoices/" + second, Map.of("clientId", aliceClientId, "lines", List.of()),
            String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> client2 = call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, Map.class).getBody();
        client2.put("address", "1 place de la Bourse");
        call(alice, HttpMethod.PUT, "/clients/" + aliceClientId, client2, Map.class);
        assertThat(call(alice, HttpMethod.POST, "/invoices/" + second + "/issue", Map.of("issueDate", "2026-08-31"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        int year = LocalDate.now().getYear();
        assertThat(call(alice, HttpMethod.POST, "/invoices/" + second + "/issue", null, Map.class).getBody())
            .containsEntry("number", year == 2026 ? "F2026-0002" : "F" + year + "-0001");
        assertThat(pdf(alice, second)).contains("Facture", "Net à payer", "1 600,50 €", "TVA non applicable, art. 293 B du CGI");

        // Paid, then not after all.
        assertThat(call(alice, HttpMethod.PUT, "/invoices/" + first + "/payment", Map.of("paidOn", "2027-01-05"), Map.class).getBody())
            .containsEntry("status", "PAID").containsEntry("paidOn", "2027-01-05").containsEntry("overdue", false);
        assertThat(call(alice, HttpMethod.PUT, "/invoices/" + first + "/payment", new HashMap<>(), Map.class).getBody())
            .containsEntry("status", "ISSUED").containsEntry("paidOn", null);

        // Bob sees none of them; the mission's deletion leaves the issued invoice.
        assertThat(call(bob, HttpMethod.GET, "/invoices/" + first, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/invoices/by-freelance/" + alice.freelanceId(), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(bob, HttpMethod.GET, "/invoices/by-freelance/" + bob.freelanceId(), null, List.class).getBody()).isEmpty();
        call(alice, HttpMethod.DELETE, "/projects/" + project, null, String.class);
        assertThat(call(alice, HttpMethod.GET, "/invoices/by-freelance/" + alice.freelanceId(), null, List.class).getBody()).hasSize(2);
    }

    @Test
    void writesChangesAndDeletesADraftOfAMission() {
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Architecte", "dailyRate", 700, "status", "WON",
            "clientId", aliceClientId), Map.class));
        Map<String, Object> line = Map.of("description", "Architecture", "quantity", 3, "unitPrice", 700, "vatRate", 20);
        Map<String, Object> draft = new HashMap<>(Map.of("clientId", aliceClientId, "projectId", project, "periodStart", "2026-10-01",
            "periodEnd", "2026-10-31", "notes", " PO 12 ", "lines", List.of(line)));
        long id = idOf(call(alice, HttpMethod.POST, "/invoices", draft, Map.class));
        assertThat(call(alice, HttpMethod.GET, "/invoices/" + id, null, Map.class).getBody()).containsEntry("projectRole", "Architecte")
            .containsEntry("notes", "PO 12").containsEntry("vat", 420.0).containsEntry("total", 2520.0).containsEntry("language", "FR");

        draft.put("periodEnd", "2026-09-30");
        assertThat(call(alice, HttpMethod.PUT, "/invoices/" + id, draft, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        draft.put("periodEnd", "2026-10-31");
        draft.remove("projectId");
        draft.put("notes", " ");
        assertThat(call(alice, HttpMethod.PUT, "/invoices/" + id, draft, Map.class).getBody()).containsEntry("projectId", null)
            .containsEntry("projectRole", null).containsEntry("notes", null);
        // Bob's client and mission cannot be named; a draft cannot be paid.
        long bobClient = idOf(call(bob, HttpMethod.POST, "/clients", Map.of("companyName", "Bob Corp", "city", "Lyon", "isFinal", true,
            "freelanceId", bob.freelanceId()), Map.class));
        draft.put("clientId", bobClient);
        assertThat(call(alice, HttpMethod.PUT, "/invoices/" + id, draft, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, "/invoices/" + id + "/payment", Map.of("paidOn", "2026-10-02"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(call(bob, HttpMethod.DELETE, "/invoices/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/invoices/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/invoices/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String pdf(Account account, long invoice) throws IOException {
        ResponseEntity<byte[]> response = call(account, HttpMethod.GET, "/invoices/" + invoice + "/pdf", null, byte[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        try (PDDocument document = Loader.loadPDF(response.getBody())) {
            return new PDFTextStripper().getText(document).replaceAll("[ \\t]+", " ");
        }
    }
}
