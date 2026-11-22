package dev.swirlit.indezy.integration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice quotes an architecture audit over HTTP: a draft, sent once her billing identity and the client's address are
 * there, numbered apart from the invoices, frozen once sent, accepted by the client, then billed by a draft invoice that
 * names it; another quote is refused. Bob can neither see nor answer them.
 */
class QuoteIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> LINE = Map.of("description", "Audit de l'architecture", "quantity", 5, "unitPrice", 800,
        "vatRate", 0);

    @Test
    @SuppressWarnings("unchecked")
    void sendsAcceptsAndInvoicesAQuote() throws IOException {
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Audit d'architecture", "dailyRate", 800, "status", "OFFER",
            "clientId", aliceClientId), Map.class));
        Map<String, Object> draft = new HashMap<>(Map.of("clientId", aliceClientId, "projectId", project, "validityDays", 15,
            "depositPercent", 30, "notes", "Démarrage sous deux semaines", "lines", List.of(LINE)));
        assertThat(call(bob, HttpMethod.POST, "/quotes", draft, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> created = call(alice, HttpMethod.POST, "/quotes", draft, Map.class).getBody();
        String quote = "/quotes/" + created.get("id");
        assertThat(created).containsEntry("status", "DRAFT").containsEntry("number", null).containsEntry("projectRole", "Audit d'architecture")
            .containsEntry("validityDays", 15).containsEntry("depositPercent", 30).containsEntry("total", 4000.0);
        assertThat(call(bob, HttpMethod.GET, "/quotes", null, List.class).getBody()).isEmpty();
        assertThat(call(alice, HttpMethod.PUT, quote + "/decision", Map.of("accepted", true), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.POST, quote + "/invoice", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pdf(alice, quote)).contains("BROUILLON — ce document n'est pas un devis envoyé")
            .contains("Offre valable 15 jours à compter de sa date d'envoi.").contains("Acompte de 30 % à la commande, soit 1 200,00 €");

        // Sent only with a complete billing identity and the client's address, then frozen.
        assertThat(call(alice, HttpMethod.POST, quote + "/send", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        call(alice, HttpMethod.PUT, "/users/billing", Map.of("legalName", "Alice Martin", "legalForm", "MICRO_ENTREPRISE",
            "siret", "55210055400012", "vatExempt", true, "address", "18 rue du Sentier\n75002 Paris", "iban", "FR7630006000011234567890189"),
            Map.class);
        assertThat(call(alice, HttpMethod.POST, quote + "/send", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> client = call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, Map.class).getBody();
        client.put("address", "29 boulevard Haussmann");
        call(alice, HttpMethod.PUT, "/clients/" + aliceClientId, client, Map.class);
        assertThat(call(alice, HttpMethod.POST, quote + "/send", Map.of("issueDate", today().plusDays(1)), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> sent = call(alice, HttpMethod.POST, quote + "/send", null, Map.class).getBody();
        String number = "D" + today().getYear() + "-0001";
        assertThat(sent).containsEntry("status", "SENT").containsEntry("number", number).containsEntry("expired", false)
            .containsEntry("issueDate", today().toString()).containsEntry("validUntil", today().plusDays(15).toString());
        assertThat(call(alice, HttpMethod.PUT, quote, draft, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.DELETE, quote, null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pdf(alice, quote)).contains("Devis").contains("N° " + number).contains("Bon pour accord")
            .contains("TVA non applicable, art. 293 B du CGI").contains("Paiement à 30 jours à réception de la facture").contains("Devis gratuit.");

        // Accepted, then billed once by a draft invoice stating the quote.
        assertThat(call(bob, HttpMethod.PUT, quote + "/decision", Map.of("accepted", true), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, quote + "/decision", Map.of(), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, quote + "/decision", Map.of("accepted", true), Map.class).getBody())
            .containsEntry("status", "ACCEPTED").containsEntry("decidedOn", today().toString());
        assertThat(call(alice, HttpMethod.PUT, quote + "/decision", Map.of("accepted", false), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(bob, HttpMethod.POST, quote + "/invoice", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Its deposit first, once: 30 % of the amount before VAT, to issue before the balance is invoiced.
        assertThat(call(bob, HttpMethod.POST, quote + "/deposit", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> deposit = call(alice, HttpMethod.POST, quote + "/deposit", null, Map.class).getBody();
        assertThat(deposit).containsEntry("kind", "DEPOSIT").containsEntry("status", "DRAFT").containsEntry("quoteNumber", number)
            .containsEntry("total", 1200.0);
        assertThat((List<Map<String, Object>>) deposit.get("lines")).singleElement().satisfies(line -> assertThat(line)
            .containsEntry("description", "Acompte de 30 % sur le devis N° " + number).containsEntry("quantity", 1.0)
            .containsEntry("unitPrice", 1200.0));
        assertThat(call(alice, HttpMethod.POST, quote + "/deposit", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.POST, quote + "/invoice", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String depositNumber = (String) call(alice, HttpMethod.POST, "/invoices/" + deposit.get("id") + "/issue", null, Map.class).getBody()
            .get("number");
        assertThat(text(call(alice, HttpMethod.GET, "/invoices/" + deposit.get("id") + "/pdf", null, byte[].class)))
            .contains("Facture d'acompte").contains("Selon devis N° " + number);

        // Then the balance, once: the quote's lines less the deposit invoiced.
        Map<String, Object> invoice = call(alice, HttpMethod.POST, quote + "/invoice", null, Map.class).getBody();
        assertThat(invoice).containsEntry("kind", "INVOICE").containsEntry("status", "DRAFT").containsEntry("quoteNumber", number)
            .containsEntry("projectId", (int) project).containsEntry("total", 2800.0);
        assertThat((List<Map<String, Object>>) invoice.get("lines")).hasSize(2).last().satisfies(line -> assertThat(line)
            .containsEntry("description", "Acompte facturé N° " + depositNumber + " du " + longDate(today())).containsEntry("quantity", -1.0)
            .containsEntry("unitPrice", 1200.0));
        assertThat(call(alice, HttpMethod.POST, quote + "/invoice", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.POST, quote + "/deposit", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/quotes", null, List.class).getBody()).singleElement()
            .satisfies(listed -> assertThat(listed).containsEntry("invoiceId", invoice.get("id")).containsEntry("depositInvoiceId", deposit.get("id")));
        String invoicePdf = text(call(alice, HttpMethod.GET, "/invoices/" + invoice.get("id") + "/pdf", null, byte[].class));
        assertThat(invoicePdf).contains("Selon devis N° " + number).contains("Acompte facturé N° " + depositNumber);
        // A deleted draft of the balance lets the quote be invoiced again.
        call(alice, HttpMethod.DELETE, "/invoices/" + invoice.get("id"), null, String.class);
        assertThat(call(alice, HttpMethod.GET, "/quotes", null, List.class).getBody().getFirst()).asInstanceOf(
            org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("invoiceId", null);
        assertThat(call(alice, HttpMethod.POST, quote + "/invoice", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Another quote, numbered next, refused; started with the default notes of the billing identity.
        Map<String, Object> billing = call(alice, HttpMethod.GET, "/users/billing", null, Map.class).getBody();
        billing.put("quoteNotes", "Devis établi sur la base d'un cahier des charges.");
        billing.put("invoiceNotes", "Merci de rappeler le numéro de facture.");
        call(alice, HttpMethod.PUT, "/users/billing", billing, Map.class);
        Map<String, Object> other = call(alice, HttpMethod.POST, "/quotes", Map.of("clientId", aliceClientId, "language", "EN",
            "lines", List.of(LINE)), Map.class).getBody();
        String refused = "/quotes/" + other.get("id");
        assertThat(other).containsEntry("validityDays", 30).containsEntry("depositPercent", null)
            .containsEntry("notes", "Devis établi sur la base d'un cahier des charges.");
        assertThat(call(alice, HttpMethod.POST, refused + "/send", null, Map.class).getBody())
            .containsEntry("number", "D" + today().getYear() + "-0002");
        assertThat(call(alice, HttpMethod.PUT, refused + "/decision", Map.of("accepted", false), Map.class).getBody())
            .containsEntry("status", "REFUSED");
        assertThat(call(alice, HttpMethod.POST, refused + "/invoice", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pdf(alice, refused)).contains("Quote").contains("This quote is free of charge.");
        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("\"quotes\"").contains(number);
    }

    @Test
    void refusesADraftWithoutLinesOrWithAnImpossibleDepositAndDeletesADraft() {
        assertThat(call(alice, HttpMethod.POST, "/quotes", Map.of("clientId", aliceClientId, "lines", List.of()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/quotes", Map.of("clientId", aliceClientId, "depositPercent", 0, "lines", List.of(LINE)),
            String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/quotes", Map.of("clientId", aliceClientId, "projectId", 999_999, "lines", List.of(LINE)),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.POST, "/quotes", Map.of("clientId", aliceClientId, "lines", List.of(Map.of("description", "Rien",
            "quantity", 0, "unitPrice", 800, "vatRate", 0))), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        long id = idOf(call(alice, HttpMethod.POST, "/quotes", Map.of("clientId", aliceClientId, "lines", List.of(LINE)), Map.class));
        assertThat(call(alice, HttpMethod.PUT, "/quotes/" + id, Map.of("clientId", aliceClientId, "validityDays", 60,
            "lines", List.of(LINE, LINE)), Map.class).getBody()).containsEntry("validityDays", 60).containsEntry("total", 8000.0);
        assertThat(call(bob, HttpMethod.DELETE, "/quotes/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/quotes/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/quotes", null, List.class).getBody()).isEmpty();
    }

    private static String longDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.FRANCE));
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Paris"));
    }

    private String pdf(Account account, String quote) throws IOException {
        return text(call(account, HttpMethod.GET, quote + "/pdf", null, byte[].class));
    }

    private static String text(ResponseEntity<byte[]> response) throws IOException {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        try (PDDocument document = Loader.loadPDF(response.getBody())) {
            return new PDFTextStripper().getText(document).replaceAll("[ \\t]+", " ");
        }
    }
}
