package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.JobOffer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The skills the open offers matching Alice's profile ask for that it lacks. */
class SkillsGapIntegrationTest extends OfferIntegrationTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void tellsTheSkillsTheMatchingOffersAskForThatTheProfileLacks() {
        Instant now = Instant.now();
        // Paris and Lyon (Java) plus 10 Java offers, 6 of which also ask for Kafka, written two ways, and 3 for Docker.
        for (int index = 0; index < 10; index++) {
            JobOffer offer = offer("gap-" + index, "Développeur Java", "Conseil " + index, now.minus(Duration.ofHours(index + 4)), 48.85, 2.35);
            offer.setSkills(index < 3 ? List.of("Java", "Kafka", "Docker") : index < 6 ? List.of("Java", "kafka") : List.of("Java"));
            offers.save(offer);
        }
        JobOffer other = offer("gap-python", "Développeur Python", "Conseil P", now.minus(Duration.ofHours(2)), 48.85, 2.35);
        other.setSkills(List.of("Python", "Django"));
        offers.save(other);

        // Without skills in the profile, nothing to compare.
        assertThat(call(alice, HttpMethod.GET, "/offers/skills-gap", null).getBody()).containsEntry("matchingOffers", 0)
            .containsEntry("gaps", List.of());

        users.findByEmail(alice.email()).ifPresent(user -> {
            user.setSkills(List.of("Java"));
            users.save(user);
        });
        Map<String, Object> gap = call(alice, HttpMethod.GET, "/offers/skills-gap", null).getBody();
        assertThat(gap).containsEntry("matchingOffers", 12);
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) gap.get("gaps");
        assertThat(gaps).extracting(entry -> entry.get("skill")).containsExactly("Kafka", "Docker");
        assertThat(gaps.getFirst()).containsEntry("offers", 6).containsEntry("percent", 50);
        assertThat(gaps.get(1)).containsEntry("offers", 3).containsEntry("percent", 25);
    }
}
