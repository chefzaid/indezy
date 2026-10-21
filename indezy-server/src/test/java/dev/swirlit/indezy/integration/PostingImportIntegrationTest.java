package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.scraping.PostingReader;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A link to a site the feed does not collect, over HTTP with two real accounts: its page's posting drafts the
 * opportunity, nothing is stored in the feed, the opportunity is created as the form corrected it with its client and
 * source named, once per workspace, and a salaried posting is refused.
 */
class PostingImportIntegrationTest extends OfferIntegrationTestSupport {

    private static final String LINK = "https://www.acme-careers.fr/postes/developpeur-java";

    @Test
    @SuppressWarnings("unchecked")
    void aPostingOnAnotherSiteDraftsTheOpportunityAddedOnceForItsWorkspace() throws Exception {
        when(postingReader.read(URI.create(LINK))).thenReturn(new PostingReader.Reading(PostingReader.Outcome.FOUND,
            ScrapedOffer.builder().title("Développeur Java").company("Acme Conseil").description("Refonte d'un SI bancaire.")
                .rateMin(550).rateMax(600).city("Lyon").postalCode("69002").url(LINK).build()));
        long collected = offers.count();

        ResponseEntity<Map> read = call(alice, HttpMethod.POST, "/offers/link", Map.of("url", LINK));

        assertThat(read.getBody()).containsEntry("status", "POSTING").containsEntry("siteName", "acme-careers.fr").doesNotContainKey("offer");
        Map<String, Object> draft = (Map<String, Object>) read.getBody().get("draft");
        assertThat(draft).containsEntry("clientName", "Acme Conseil").containsEntry("sourceName", "acme-careers.fr")
            .containsEntry("sourceLink", "https://www.acme-careers.fr");
        Map<String, Object> project = new HashMap<>((Map<String, Object>) draft.get("project"));
        assertThat(project).containsEntry("role", "Développeur Java").containsEntry("dailyRate", 600).containsEntry("link", LINK);
        assertThat(offers.count()).isEqualTo(collected);

        project.put("role", "Tech lead Java");
        Map<String, Object> request = Map.of("project", project, "clientName", "Acme Conseil", "sourceName", "acme-careers.fr",
            "sourceLink", "https://www.acme-careers.fr");
        ResponseEntity<Map> created = call(alice, HttpMethod.POST, "/offers/postings/import", request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("role", "Tech lead Java").containsEntry("status", "CONTACT")
            .containsEntry("clientName", "Acme Conseil").containsEntry("sourceName", "acme-careers.fr").containsEntry("link", LINK);
        long projectId = ((Number) created.getBody().get("id")).longValue();

        // Once per mission: the link opens the opportunity, without reading the page again, and a second import is refused.
        assertThat(call(alice, HttpMethod.POST, "/offers/link", Map.of("url", LINK)).getBody())
            .containsEntry("status", "IN_PIPELINE").containsEntry("projectId", (int) projectId);
        assertThat(call(alice, HttpMethod.POST, "/offers/postings/import", request).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Bob's workspace has no opportunity from it.
        assertThat(call(bob, HttpMethod.POST, "/offers/link", Map.of("url", LINK)).getBody()).containsEntry("status", "POSTING");
        verify(postingReader, times(2)).read(URI.create(LINK));
    }

    @Test
    void aSalariedPostingOnAnotherSiteIsRefused() throws Exception {
        String permanent = "https://www.acme-careers.fr/postes/comptable";
        when(postingReader.read(URI.create(permanent))).thenReturn(new PostingReader.Reading(PostingReader.Outcome.NOT_FREELANCE,
            ScrapedOffer.builder().title("Comptable").url(permanent).build()));

        assertThat(call(alice, HttpMethod.POST, "/offers/link", Map.of("url", permanent)).getBody())
            .containsEntry("status", "NOT_FREELANCE").doesNotContainKey("draft");
    }
}
