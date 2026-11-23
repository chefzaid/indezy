package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice matches her bank statement's credits with her invoices awaiting payment, then marks the matches paid. */
class BankReconciliationIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void matchesTheCreditsByNumberClientAndAmount() {
        call(alice, HttpMethod.PUT, "/users/billing", Map.of("legalName", "Alice Martin", "legalForm", "EI", "siret", "55210055400012",
            "vatExempt", true, "address", "Paris", "iban", "FR7630006000011234567890189"), Map.class);
        Map<String, Object> byNumber = issued("Rapprochement Conseil", "1234.56");
        Map<String, Object> byClient = issued("Banque Rouge Rapprochée", "987.65");
        Map<String, Object> byAmount = issued("Rapprochement Autre", "456.78");
        issued("Duo Un", "321.09");
        issued("Duo Deux", "321.09");

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String number = ((String) byNumber.get("number")).replace("-", " ");
        String statement = "Compte courant;FR76\n\nDate;Libellé;Montant\n" +
            today + ";VIR SEPA FACT " + number + ";1 234,56\n" +
            today + ";VIR BANQUE ROUGE RAPPROCHEE;987,65\n" +
            today + ";VIREMENT RECU 0042;456,78\n" +
            today + ";VIR DIVERS;321,09\n" +
            today + ";PRLV ASSURANCE;-55,00\n" +
            today + ";REMISE CHEQUE;77,77\n";
        List<Map<String, Object>> matches = call(alice, HttpMethod.POST, "/invoices/reconciliation", Map.of("content", statement), List.class)
            .getBody();
        assertThat(matches).extracting(match -> match.get("label") + " " + match.get("invoiceId") + " " + match.get("reason"))
            .containsExactly("VIR SEPA FACT " + number + " " + byNumber.get("id") + " NUMBER",
                "VIR BANQUE ROUGE RAPPROCHEE " + byClient.get("id") + " CLIENT",
                "VIREMENT RECU 0042 " + byAmount.get("id") + " AMOUNT",
                "VIR DIVERS null null", "REMISE CHEQUE null null");
        assertThat(matches.get(1)).containsEntry("clientName", "Banque Rouge Rapprochée").containsEntry("amount", 987.65)
            .containsEntry("date", LocalDate.now().toString());

        // Kept, it is paid like any invoice, then no longer awaits a credit.
        call(alice, HttpMethod.PUT, "/invoices/" + byAmount.get("id") + "/payment", Map.of("paidOn", LocalDate.now().toString()), Map.class);
        assertThat(((List<Map<String, Object>>) call(alice, HttpMethod.POST, "/invoices/reconciliation", Map.of("content", statement), List.class)
            .getBody()).get(2)).containsEntry("invoiceId", null);
        assertThat((List<Map<String, Object>>) call(bob, HttpMethod.POST, "/invoices/reconciliation", Map.of("content", statement), List.class)
            .getBody()).allSatisfy(match -> assertThat(match.get("invoiceId")).isNull());
        assertThat(call(alice, HttpMethod.POST, "/invoices/reconciliation", Map.of("content", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> issued(String clientName, String amount) {
        long client = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", clientName, "city", "Lyon", "isFinal", true,
            "address", "1 place de la Bourse", "freelanceId", alice.freelanceId()), Map.class));
        long invoice = idOf(call(alice, HttpMethod.POST, "/invoices", Map.of("clientId", client, "lines",
            List.of(Map.of("description", "Mission", "quantity", 1, "unitPrice", amount, "vatRate", 0))), Map.class));
        return call(alice, HttpMethod.POST, "/invoices/" + invoice + "/issue", null, Map.class).getBody();
    }
}
