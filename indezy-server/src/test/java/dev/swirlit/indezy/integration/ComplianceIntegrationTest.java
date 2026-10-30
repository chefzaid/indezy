package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice keeps the documents ESNs ask for, over HTTP: stored with their dates, the certificate to renew among her
 * reminders, then shared through a link that shows them without an account until she revokes it. Bob sees none.
 */
class ComplianceIntegrationTest extends TwoAccountsIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Test
    @SuppressWarnings("unchecked")
    void storesRemindsAndSharesDocuments() throws Exception {
        LocalDate today = LocalDate.now();
        // An URSSAF certificate issued five months and ten days ago: six months valid, so 20 days left.
        Map<String, Object> urssaf = upload(alice, "attestation.pdf", PDF, "URSSAF_VIGILANCE", null,
            today.plusDays(20).minusMonths(6), null).getBody();
        assertThat(urssaf).containsEntry("type", "URSSAF_VIGILANCE").containsEntry("fileName", "attestation.pdf")
            .containsEntry("contentType", "application/pdf").containsEntry("expiresOn", today.plusDays(20).toString())
            .containsEntry("validity", "EXPIRING");
        long urssafId = ((Number) urssaf.get("id")).longValue();
        Map<String, Object> rib = upload(alice, "rib.png", PNG, "BANK_DETAILS", null, null, null).getBody();
        assertThat(rib).containsEntry("validity", "NO_EXPIRY").containsEntry("contentType", "image/png");
        long ribId = ((Number) rib.get("id")).longValue();
        long insurance = ((Number) upload(alice, "rc-pro.pdf", PDF, "PROFESSIONAL_INSURANCE", " RC Pro Hiscox ", today.minusYears(1),
            today.minusDays(2)).getBody().get("id")).longValue();

        // Refused: another document without a name, a web page, an end before the issue.
        assertThat(upload(alice, "x.pdf", PDF, "OTHER", " ", null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(alice, "x.pdf", PDF, "OTHER", "x".repeat(101), null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(alice, "page.pdf", "<html>".getBytes(StandardCharsets.UTF_8), "IDENTITY", null, null, null).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/compliance-documents/" + insurance, Map.of("type", "PROFESSIONAL_INSURANCE",
            "issuedOn", today.toString(), "expiresOn", today.minusDays(1).toString()), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/compliance-documents/" + insurance, Map.of("type", "PROFESSIONAL_INSURANCE",
            "label", "RC Pro", "issuedOn", today.minusYears(1).toString(), "expiresOn", today.minusDays(2).toString()), Map.class).getBody())
            .containsEntry("label", "RC Pro").containsEntry("validity", "EXPIRED");

        List<Map<String, Object>> listed = call(alice, HttpMethod.GET, "/compliance-documents", null, List.class).getBody();
        assertThat(listed).extracting(item -> item.get("type")).containsExactly("URSSAF_VIGILANCE", "PROFESSIONAL_INSURANCE", "BANK_DETAILS");
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/projects/stats/dashboard/" + alice.freelanceId(), null,
            Map.class).getBody().get("expiringDocuments")).extracting(item -> item.get("type"), item -> item.get("daysLeft"))
            .containsExactly(org.assertj.core.groups.Tuple.tuple("PROFESSIONAL_INSURANCE", -2), org.assertj.core.groups.Tuple.tuple(
                "URSSAF_VIGILANCE", 20));
        ResponseEntity<byte[]> content = call(alice, HttpMethod.GET, "/compliance-documents/" + urssafId + "/content", null, byte[].class);
        assertThat(content.getBody()).isEqualTo(PDF);
        assertThat(content.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
        assertThat(content.getHeaders().getFirst("Content-Security-Policy")).isEqualTo("sandbox; default-src 'none'");
        assertThat(content.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");

        // Bob sees, reads, changes, deletes and shares none of them.
        assertThat(call(bob, HttpMethod.GET, "/compliance-documents", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/compliance-documents/" + urssafId + "/content", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/compliance-documents/" + urssafId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/compliance-packs", Map.of("recipient", "Bob's ESN", "documentIds", List.of(urssafId)),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // A link for the ESN: its documents without an account, counted, the token itself never stored.
        Map<String, Object> created = call(alice, HttpMethod.POST, "/compliance-packs", Map.of("recipient", " Capgemini ",
            "documentIds", List.of(urssafId, ribId), "days", 7), Map.class).getBody();
        String token = (String) created.get("token");
        Map<String, Object> pack = (Map<String, Object>) created.get("pack");
        long packId = ((Number) pack.get("id")).longValue();
        assertThat(pack).containsEntry("recipient", "Capgemini").containsEntry("active", true).containsEntry("openCount", 0);
        assertThat(jdbcTemplate.queryForObject("SELECT token_hash FROM compliance_packs WHERE id = ?", String.class, packId))
            .hasSize(64).isNotEqualTo(token);
        Map<String, Object> shared = open(token).getBody();
        assertThat(shared).containsEntry("recipient", "Capgemini").containsKey("sharedBy");
        assertThat((List<Map<String, Object>>) shared.get("documents")).extracting(item -> item.get("type"))
            .containsExactlyInAnyOrder("URSSAF_VIGILANCE", "BANK_DETAILS");
        ResponseEntity<byte[]> download = exchange("/public/compliance-packs/documents/" + ribId, HttpMethod.GET, linked(token), byte[].class);
        assertThat(download.getBody()).isEqualTo(PNG);
        assertThat(download.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(exchange("/public/compliance-packs/documents/" + insurance, HttpMethod.GET, linked(token), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(open("not-a-token").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // A deleted document leaves the link; a revoked link opens no more but stays listed.
        call(alice, HttpMethod.DELETE, "/compliance-documents/" + ribId, null, Void.class);
        assertThat((List<?>) open(token).getBody().get("documents")).hasSize(1);
        assertThat(exchange("/public/compliance-packs/documents/" + ribId, HttpMethod.GET, linked(token), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/compliance-packs/" + packId + "/revoke", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.POST, "/compliance-packs/" + packId + "/revoke", null, Map.class).getBody())
            .containsEntry("active", false).containsEntry("openCount", 2);
        assertThat(open(token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/compliance-packs", null, List.class).getBody()).hasSize(1);

        Map<String, Object> export = JsonMapper.builder().build()
            .readValue(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody(), Map.class);
        assertThat((List<?>) export.get("complianceDocuments")).hasSize(2);
        assertThat(export.get("compliancePacks").toString()).doesNotContain(token);
    }

    private ResponseEntity<Map> upload(Account account, String name, byte[] content, String type, String label, LocalDate issuedOn,
                                       LocalDate expiresOn) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        form.add("type", type);
        if (label != null) {
            form.add("label", label);
        }
        if (issuedOn != null) {
            form.add("issuedOn", issuedOn.toString());
        }
        if (expiresOn != null) {
            form.add("expiresOn", expiresOn.toString());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return exchange("/compliance-documents", HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }

    private ResponseEntity<Map> open(String token) {
        return exchange("/public/compliance-packs", HttpMethod.GET, linked(token), Map.class);
    }

    private static HttpEntity<Void> linked(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Pack-Token", token);
        return new HttpEntity<>(headers);
    }
}
