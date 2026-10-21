package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class PostingSiteTest {

    @Test
    void namesAJobBoardIndezyKnowsAndAnyOtherSiteByItsHost() {
        assertThat(PostingSite.of(URI.create("https://www.welcometothejungle.com/fr/companies/acme/jobs/dev")))
            .isEqualTo(new PostingSite("Welcome to the Jungle", "https://www.welcometothejungle.com"));
        assertThat(PostingSite.of(URI.create("https://app.welcometothejungle.com/jobs/1")).name()).isEqualTo("Welcome to the Jungle");
        assertThat(PostingSite.of(URI.create("https://www.free-work.com/fr/tech-it/job-mission/1")).name()).isEqualTo("Free-Work");
        assertThat(PostingSite.of(URI.create("https://www.acme-careers.fr/postes/42")))
            .isEqualTo(new PostingSite("acme-careers.fr", "https://www.acme-careers.fr"));
        // Not a board's subdomain, only a look-alike.
        assertThat(PostingSite.of(URI.create("https://notwelcometothejungle.com/jobs/1")).name()).isEqualTo("notwelcometothejungle.com");
    }
}
