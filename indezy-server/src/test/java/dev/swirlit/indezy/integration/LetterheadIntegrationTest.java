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
}
