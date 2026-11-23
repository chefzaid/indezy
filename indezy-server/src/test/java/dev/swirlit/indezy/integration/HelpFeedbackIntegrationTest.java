package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Readers, with an account or not, say whether a help article helped; the totals are counted for the administrators only. */
class HelpFeedbackIntegrationTest extends TwoAccountsIntegrationTest {

    private HttpStatus answer(String slug, Object helpful) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return HttpStatus.valueOf(exchange("/public/help/" + slug + "/feedback", HttpMethod.POST,
            new HttpEntity<>(helpful == null ? Map.of() : Map.of("helpful", helpful), headers), String.class).getStatusCode().value());
    }

    @Test
    void countsEachArticlesAnswersWithoutWhoGaveThem() {
        assertThat(answer("feedback-offers", true)).isEqualTo(HttpStatus.NO_CONTENT);
        answer("feedback-offers", true);
        answer("feedback-offers", false);
        answer("feedback-dashboard", false);
        // Not an article's name: nothing counted; no answer: refused.
        assertThat(answer("Not_An_Article", true)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(answer("feedback-offers", null)).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(jdbcTemplate.queryForList("SELECT slug, helpful, unhelpful FROM help_feedback_days WHERE slug LIKE 'feedback-%' ORDER BY slug"))
            .extracting(row -> row.get("slug") + " " + row.get("helpful") + "/" + row.get("unhelpful"))
            .containsExactly("feedback-dashboard 0/1", "feedback-offers 2/1");
        assertThat(call(alice, HttpMethod.GET, "/admin/help-feedback", null, String.class).getStatusCode()).isIn(HttpStatus.FORBIDDEN,
            HttpStatus.NOT_FOUND);
    }
}
