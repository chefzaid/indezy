package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Each workspace's seller identity and its clients' billing identifiers, over HTTP. */
class BillingIdentityIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void keepsEachWorkspacesIdentityAndChecksIt() {
        Map<String, Object> defaults = call(alice, HttpMethod.GET, "/users/billing", null, Map.class).getBody();
        assertThat(defaults).containsEntry("legalName", "alice Test").containsEntry("vatExempt", true).containsEntry("vatRate", 20.0)
            .containsEntry("paymentTermDays", 30).containsEntry("invoicePrefix", "F").containsEntry("latePaymentRate", null)
            .containsEntry("complete", false);

        Map<String, Object> identity = new HashMap<>(Map.of("legalName", "Alice Martin", "legalForm", "MICRO_ENTREPRISE",
            "siret", "123 456 789 00012", "vatExempt", true, "address", "18 rue du Sentier\n75002 Paris",
            "iban", "fr76 3000 6000 0112 3456 7890 189", "bic", "bnpafrpp", "paymentTermDays", 45, "latePaymentRate", 12.15));
        Map<String, Object> saved = call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getBody();
        assertThat(saved).containsEntry("siret", "12345678900012").containsEntry("iban", "FR7630006000011234567890189")
            .containsEntry("bic", "BNPAFRPP").containsEntry("paymentTermDays", 45).containsEntry("latePaymentRate", 12.15)
            .containsEntry("complete", true);
        assertThat(call(bob, HttpMethod.GET, "/users/billing", null, Map.class).getBody()).containsEntry("siret", null);
        // The terms and conditions printed after each quote, kept as written; too long ones refused.
        identity.put("quoteTerms", "  Article 1 — Objet\nToute prestation.  ");
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getBody())
            .containsEntry("quoteTerms", "Article 1 — Objet\nToute prestation.");
        Map<String, Object> tooLong = new HashMap<>(identity);
        tooLong.put("quoteTerms", "x".repeat(20_001));
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", tooLong, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        identity.remove("quoteTerms");
        // An accent colour dark enough to read; a pale one is refused.
        identity.put("accentColor", "#1f4e79");
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getBody()).containsEntry("accentColor", "#1F4E79")
            .doesNotContainKey("accentReadable");
        Map<String, Object> pale = new HashMap<>(identity);
        pale.put("accentColor", "#FFEE88");
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", pale, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        identity.remove("accentColor");

        // A wrong IBAN, SIRET or payment term is refused, and the saved identity kept.
        for (Map.Entry<String, Object> wrong : Map.<String, Object>of("iban", "FR7630006000011234567890188", "siret", "1234",
            "paymentTermDays", 90, "latePaymentRate", 101).entrySet()) {
            Map<String, Object> request = new HashMap<>(identity);
            request.put(wrong.getKey(), wrong.getValue());
            assertThat(call(alice, HttpMethod.PUT, "/users/billing", request, String.class).getStatusCode()).as(wrong.getKey())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        // Subject to VAT without a VAT number: not complete.
        identity.put("vatExempt", false);
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getBody()).containsEntry("complete", false);

        Map<String, Object> client = call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, Map.class).getBody();
        client.put("siren", "552 100 554");
        client.put("vatNumber", "fr 40 552100554");
        assertThat(call(alice, HttpMethod.PUT, "/clients/" + aliceClientId, client, Map.class).getBody())
            .containsEntry("siren", "552100554").containsEntry("vatNumber", "FR40552100554");
        client.put("siren", "55210");
        assertThat(call(alice, HttpMethod.PUT, "/clients/" + aliceClientId, client, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
