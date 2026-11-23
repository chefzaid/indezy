package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.pdf.LetterheadTest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice draws her invoices over her letterhead; a US Letter page is refused; Bob's stay on blank pages. */
class LetterheadIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    void drawsTheInvoicesOverTheLetterhead() throws IOException {
        ResponseEntity<Map> uploaded = upload(LetterheadTest.letterhead(PDRectangle.A4, true, "Papier Alice Conseil"));
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object letterhead = uploaded.getBody().get("letterheadId");
        assertThat(letterhead).isNotNull();
        assertThat(call(alice, HttpMethod.GET, "/users/billing/letterheads/" + letterhead, null, byte[].class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(bob, HttpMethod.GET, "/users/billing/letterheads/" + letterhead, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        // Saving the form keeps the letterhead, which it never sets.
        Map<String, Object> identity = new HashMap<>(call(alice, HttpMethod.GET, "/users/billing", null, Map.class).getBody());
        identity.put("letterheadId", null);
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getBody()).containsEntry("letterheadId", letterhead);
        assertThat(upload(LetterheadTest.letterhead(PDRectangle.LETTER, true, "US Letter")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        long invoice = idOf(call(alice, HttpMethod.POST, "/invoices", Map.of("clientId", aliceClientId, "language", "FR",
            "lines", List.of(Map.of("description", "Audit", "quantity", 2, "unitPrice", 650, "vatRate", 20))), Map.class));
        assertThat(text(call(alice, HttpMethod.GET, "/invoices/" + invoice + "/pdf", null, byte[].class).getBody())).contains("Papier Alice Conseil", "Audit");

        assertThat(call(alice, HttpMethod.DELETE, "/users/billing/letterhead", null, Map.class).getBody()).containsEntry("letterheadId", null);
        assertThat(text(call(alice, HttpMethod.GET, "/invoices/" + invoice + "/pdf", null, byte[].class).getBody())).doesNotContain("Papier");
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private ResponseEntity<Map> upload(byte[] content) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "papier.pdf";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(alice.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return exchange("/users/billing/letterhead", HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anIssuedInvoiceKeepsTheStyleItWasIssuedWith() throws IOException {
        upload(LetterheadTest.letterhead(PDRectangle.A4, true, "Papier de novembre"));
        Map<String, Object> identity = new HashMap<>(call(alice, HttpMethod.GET, "/users/billing", null, Map.class).getBody());
        identity.putAll(Map.of("legalName", "Alice Martin", "legalForm", "MICRO_ENTREPRISE", "siret", "55210055400012", "address",
            "18 rue du Sentier\n75002 Paris", "iban", "FR7630006000011234567890189", "vatExempt", true, "header", "Conseil Java",
            "footer", "alice.dev", "accentColor", "#1F4E79", "font", "SERIF"));
        identity.put("theme", "CLASSIC");
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> client = new HashMap<>(call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, Map.class).getBody());
        client.put("address", "1 place de la Bourse");
        call(alice, HttpMethod.PUT, "/clients/" + aliceClientId, client, Map.class);
        long invoice = idOf(call(alice, HttpMethod.POST, "/invoices", Map.of("clientId", aliceClientId, "language", "FR",
            "lines", List.of(Map.of("description", "Audit", "quantity", 2, "unitPrice", 650, "vatRate", 0))), Map.class));
        assertThat(call(alice, HttpMethod.POST, "/invoices/" + invoice + "/issue", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        byte[] issued = call(alice, HttpMethod.GET, "/invoices/" + invoice + "/pdf", null, byte[].class).getBody();

        // Every style setting changed or removed afterwards.
        call(alice, HttpMethod.DELETE, "/users/billing/letterhead", null, Map.class);
        identity.putAll(Map.of("header", "Autre accroche", "footer", "autre.dev", "accentColor", "#7A1F1F", "font", "SANS", "theme", "MODERN"));
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getBody()).containsEntry("theme", "MODERN");

        byte[] again = call(alice, HttpMethod.GET, "/invoices/" + invoice + "/pdf", null, byte[].class).getBody();
        assertThat(text(again)).contains("Papier de novembre", "Conseil Java", "alice.dev").doesNotContain("Autre accroche", "autre.dev");
        try (PDDocument document = Loader.loadPDF(again)) {
            org.apache.pdfbox.pdmodel.PDResources resources = document.getPage(0).getResources();
            java.util.List<String> fonts = new java.util.ArrayList<>();
            for (org.apache.pdfbox.cos.COSName name : resources.getFontNames()) {
                fonts.add(resources.getFont(name).getName());
            }
            assertThat(fonts).anyMatch(font -> font.contains("LiberationSerif"));
        }
        assertThat(text(again)).isEqualTo(text(issued));
    }

    @Test
    void previewsTheStyleTheFormSetsBeforeSavingIt() throws IOException {
        Map<String, Object> identity = new HashMap<>(call(alice, HttpMethod.GET, "/users/billing", null, Map.class).getBody());
        identity.put("theme", "MODERN");
        identity.put("accentColor", "#1F4E79");
        ResponseEntity<byte[]> preview = call(alice, HttpMethod.POST, "/invoices/style-preview", identity, byte[].class);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        java.awt.image.BufferedImage page = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(preview.getBody()));
        // An A4 page whose top is the modern band in the accent colour, though nothing was saved.
        assertThat(page.getHeight()).isGreaterThan(page.getWidth());
        assertThat(new java.awt.Color(page.getRGB(page.getWidth() / 2, 4))).isEqualTo(new java.awt.Color(0x1F, 0x4E, 0x79));
        assertThat(call(alice, HttpMethod.GET, "/users/billing", null, Map.class).getBody()).containsEntry("theme", "SIMPLE");
        identity.put("accentColor", "nope");
        assertThat(call(alice, HttpMethod.POST, "/invoices/style-preview", identity, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
