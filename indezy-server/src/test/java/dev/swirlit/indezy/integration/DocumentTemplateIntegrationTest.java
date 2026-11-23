package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.pdf.BillingDocxTest;
import dev.swirlit.indezy.pdf.DocxTemplateTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice lays out her invoices in her own Word template; the Word copy fills it; Bob's keep the built-in layout. */
class DocumentTemplateIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void fillsTheWorkspacesWordTemplate() throws IOException {
        assertThat(call(alice, HttpMethod.GET, "/users/billing/templates/placeholders", null, List.class).getBody())
            .contains("document.number", "#lines", "logo");
        ResponseEntity<Map> uploaded = upload("INVOICE", "Mon modèle.docx", DocxTemplateTest.template());
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploaded.getBody()).containsEntry("kind", "INVOICE").containsEntry("fileName", "Mon modèle.docx")
            .containsEntry("unknown", List.of("iban.inconnu")).containsEntry("missing", List.of("mentions"));
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/billing/templates", null, List.class).getBody())
            .extracting(template -> template.get("kind")).containsExactly("INVOICE");
        assertThat(upload("QUOTE", "notes.txt", "hello".getBytes(StandardCharsets.UTF_8)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Before the first invoice, the preview fills the template with a sample one.
        String sample = BillingDocxTest.text(BillingDocxTest.parts(call(alice, HttpMethod.GET, "/invoices/template-preview", null, byte[].class)
            .getBody()));
        assertThat(sample).contains("Prestation de conseil (exemple)", "Client exemple", "3 600,00 €");
        assertThat(call(alice, HttpMethod.GET, "/quotes/template-preview", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        long invoice = idOf(call(alice, HttpMethod.POST, "/invoices", Map.of("clientId", aliceClientId, "language", "FR",
            "lines", List.of(Map.of("description", "Audit", "quantity", 2, "unitPrice", 650, "vatRate", 20))), Map.class));
        String filled = BillingDocxTest.text(BillingDocxTest.parts(call(alice, HttpMethod.GET, "/invoices/" + invoice + "/docx", null, byte[].class)
            .getBody()));
        assertThat(filled).contains("Audit", "1 560,00 €", "Pas d'escompte", "{{iban.inconnu}}").doesNotContain("{{line.description}}");
        // Then with the latest one.
        assertThat(BillingDocxTest.text(BillingDocxTest.parts(call(alice, HttpMethod.GET, "/invoices/template-preview", null, byte[].class)
            .getBody()))).contains("Audit").doesNotContain("exemple");
        assertThat(call(bob, HttpMethod.GET, "/users/billing/templates", null, List.class).getBody()).isEmpty();

        assertThat(call(alice, HttpMethod.DELETE, "/users/billing/templates/INVOICE", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        String builtIn = BillingDocxTest.text(BillingDocxTest.parts(call(alice, HttpMethod.GET, "/invoices/" + invoice + "/docx", null, byte[].class)
            .getBody()));
        assertThat(builtIn).contains("Facturé à").doesNotContain("{{iban.inconnu}}");
    }

    @Test
    void fillsTheWorkspacesPdfFormForItsQuotes() throws IOException {
        byte[] form = dev.swirlit.indezy.pdf.PdfFormTemplateTest.form(List.of("quote.number", "client_name", "line_description_1", "line_amount_1",
            "totals_toPay", "Visa"));
        ResponseEntity<Map> uploaded = upload("QUOTE", "formulaire.pdf", form);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploaded.getBody()).containsEntry("fileName", "formulaire.pdf").containsEntry("unknown", List.of("Visa"))
            .containsEntry("missing", List.of("date", "seller", "mentions"));
        assertThat(upload("INVOICE", "vide.pdf", dev.swirlit.indezy.pdf.PdfFormTemplateTest.form(List.of())).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        long quote = idOf(call(alice, HttpMethod.POST, "/quotes", Map.of("clientId", aliceClientId, "language", "FR",
            "lines", List.of(Map.of("description", "Cadrage", "quantity", 3, "unitPrice", 700, "vatRate", 20))), Map.class));
        ResponseEntity<byte[]> copy = call(alice, HttpMethod.GET, "/quotes/" + quote + "/docx", null, byte[].class);
        assertThat(copy.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(copy.getHeaders().getContentDisposition().getFilename()).endsWith(".pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(copy.getBody())) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document)).contains("Cadrage", "2 100,00 €");
        }
        assertThat(call(alice, HttpMethod.GET, "/quotes/template-preview", null, byte[].class).getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PDF);
    }

    @Test
    void startsAWordTemplateFromTheBuiltInLayoutThatUploadsWithNothingMissing() throws IOException {
        ResponseEntity<byte[]> starter = call(alice, HttpMethod.GET, "/users/billing/templates/INVOICE/starter?language=FR", null, byte[].class);
        assertThat(starter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(starter.getHeaders().getContentDisposition().getFilename()).isEqualTo("modele-facture.docx");
        ResponseEntity<Map> uploaded = upload("INVOICE", "modele-facture.docx", starter.getBody());
        assertThat(uploaded.getBody()).containsEntry("unknown", List.of()).containsEntry("missing", List.of());
        assertThat(call(alice, HttpMethod.GET, "/users/billing/templates/QUOTE/starter?language=EN", null, byte[].class).getHeaders()
            .getContentDisposition().getFilename()).isEqualTo("quote-template.docx");
        ResponseEntity<byte[]> modern = call(alice, HttpMethod.GET, "/users/billing/templates/INVOICE/starter?language=FR&theme=MODERN", null,
            byte[].class);
        assertThat(new String(dev.swirlit.indezy.pdf.BillingDocxTest.parts(modern.getBody()).get("word/document.xml"),
            java.nio.charset.StandardCharsets.UTF_8)).contains("w:fill=");

        // The filled example: a sample numbered and dated today, nothing left to fill.
        ResponseEntity<byte[]> example = call(alice, HttpMethod.GET, "/users/billing/templates/QUOTE/example?language=EN&theme=CLASSIC", null,
            byte[].class);
        assertThat(example.getHeaders().getContentDisposition().getFilename()).isEqualTo("quote-example.docx");
        assertThat(dev.swirlit.indezy.pdf.BillingDocxTest.text(dev.swirlit.indezy.pdf.BillingDocxTest.parts(example.getBody())))
            .contains("Quote", "No. D" + java.time.LocalDate.now().getYear() + "-0001", "Consulting (example)", "Client exemple", "Amount due: ")
            .doesNotContain("{{");
        assertThat(dev.swirlit.indezy.pdf.BillingDocxTest.text(dev.swirlit.indezy.pdf.BillingDocxTest.parts(call(alice, HttpMethod.GET,
            "/users/billing/templates/INVOICE/example?language=FR", null, byte[].class).getBody()))).contains("Facture", "Prestation de conseil (exemple)")
            .doesNotContain("{{");
    }

    private ResponseEntity<Map> upload(String kind, String name, byte[] content) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(alice.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return exchange("/users/billing/templates/" + kind, HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }
}
