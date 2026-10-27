package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.service.OfferMentionIndexer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice matches offers on her profile's skills while their mentions are not recorded yet, then once recorded, then
 * after an offer's text changed: the same offers each time, and the terms and mentions recorded as she searches.
 */
class OfferMentionIntegrationTest extends OfferIntegrationTestSupport {

    @Autowired
    private OfferMentionIndexer indexer;

    @Autowired
    private JdbcTemplate jdbc;

    private void profileSkills(String... skills) {
        users.findByEmail(alice.email()).ifPresent(user -> {
            user.setSkills(List.of(skills));
            users.save(user);
        });
    }

    private List<Object> matching() {
        return titles(call(alice, HttpMethod.GET, "/offers?matchesProfile=true", null));
    }

    private List<String> mentioned(Long offerId) {
        return jdbc.queryForList("SELECT term FROM offer_mentions WHERE offer_id = ? ORDER BY term", String.class, offerId);
    }

    @Test
    void matchesTheSameOffersBeforeAndAfterTheirMentionsAreRecorded() {
        profileSkills("Java");
        // Not recorded yet: read on their text. The search adds the profile's term to the vocabulary.
        assertThat(matching()).containsExactly("Développeur Java Spring");
        assertThat(jdbc.queryForList("SELECT term FROM offer_terms WHERE indexed = TRUE", String.class)).contains("java");

        assertThat(indexer.indexPending(10)).isGreaterThanOrEqualTo(2);
        assertThat(indexer.indexPending(10)).isZero();
        assertThat(mentioned(paris.getId())).contains("java");
        assertThat(mentioned(lyon.getId())).isEmpty();
        assertThat(matching()).containsExactly("Développeur Java Spring");

        // A new term is read in the recorded offers at once.
        profileSkills("Cloud");
        assertThat(matching()).containsExactly("Architecte cloud");
        assertThat(mentioned(lyon.getId())).containsExactly("cloud");

        // A changed text is read again until it is recorded anew.
        var changed = offers.findById(paris.getId()).orElseThrow();
        changed.setSearchText("developpeur cloud native");
        offers.save(changed);
        assertThat(offers.findById(paris.getId()).orElseThrow().getMentionsIndexedAt()).isNull();
        assertThat(matching()).containsExactlyInAnyOrder("Développeur Java Spring", "Architecte cloud");
        assertThat(indexer.indexPending(10)).isEqualTo(1);
        assertThat(mentioned(paris.getId())).containsExactly("cloud");
        profileSkills("Java");
        assertThat(matching()).isEmpty();

        // A short term ("Go") is read in the skill list; an offer without text is recorded too.
        jdbc.update("UPDATE job_offer_skills SET skill = 'Go' WHERE offer_id = ?", lyon.getId());
        jdbc.update("UPDATE job_offers SET mentions_indexed_at = NULL WHERE id = ?", lyon.getId());
        var blank = offer("fw-3", "Sans texte", null, java.time.Instant.now(), 48.8, 2.3);
        blank.setSearchText(null);
        offers.save(blank);
        assertThat(indexer.indexPending(10)).isEqualTo(2);
        profileSkills("Go");
        assertThat(matching()).containsExactly("Architecte cloud");
        assertThat(mentioned(lyon.getId())).contains("go");
        assertThat(offers.findById(blank.getId()).orElseThrow().getMentionsIndexedAt()).isNotNull();
    }
}
