package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.OfferMarketQueries.Figures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The market snapshot's reads find what the feed finds, whatever its sort. */
@DataJpaTest
@Import(OfferMarketQueries.class)
@ActiveProfiles("test")
class OfferMarketQueriesTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OfferMarketQueries queries;

    @BeforeEach
    void setUp() {
        persist("java", OfferSite.FREE_WORK, offer -> {
            offer.setSearchText("developpeur java spring paris");
            offer.setRateMin(550);
            offer.setRateMax(650);
            offer.setWorkMode(WorkMode.HYBRID);
            offer.setRemoteDaysPerWeek(2);
            offer.setCompany("Exemple Conseil");
            offer.setSkills(List.of("Java", "Spring", "java"));
            offer.setCopyGroup(900L);
        });
        // The same mission on another site, an hour later: counted once.
        persist("java-copy", OfferSite.COLLECTIVE, offer -> {
            offer.setSearchText("developpeur java spring paris");
            offer.setSkills(List.of("Java"));
            offer.setPublishedAt(NOW.minus(Duration.ofHours(1)));
            offer.setCopyGroup(900L);
        });
        persist("kafka", OfferSite.FREE_WORK, offer -> {
            offer.setSearchText("tech lead kafka lyon");
            offer.setRateMax(700);
            offer.setWorkMode(WorkMode.REMOTE);
            offer.setSkills(List.of("JAVA", "Kafka"));
        });
        persist("closed", OfferSite.FREE_WORK, offer -> {
            offer.setSearchText("developpeur java nantes");
            offer.setSkills(List.of("Java"));
            offer.setExpiresAt(NOW.minus(Duration.ofDays(1)));
        });
        entityManager.flush();
    }

    @Test
    void readsTheFiguresOfEachMissionTheFeedFinds() {
        OfferSearchRequest request = new OfferSearchRequest();
        request.setSort(OfferSearchRequest.Sort.RATE);

        assertThat(queries.figures(found(request)))
            .extracting(Figures::rateMin, Figures::rateMax, Figures::workMode, Figures::remoteDaysPerWeek, Figures::company)
            .containsExactlyInAnyOrder(tuple(550, 650, WorkMode.HYBRID, 2, "Exemple Conseil"), tuple(null, 700, WorkMode.REMOTE, null, null));
        request.setQ("paris");
        assertThat(queries.figures(found(request))).extracting(Figures::rateMax).containsExactly(650);
    }

    @Test
    void ranksTheSkillsOfTheFoundOffersWhateverTheirCase() {
        OfferSearchRequest request = new OfferSearchRequest();
        request.setSort(OfferSearchRequest.Sort.MATCH);

        List<OfferFacetDto> skills = queries.topSkills(found(request), 10);

        // "Java", "java" and "JAVA" are one skill, which the Java offer names twice but counts once.
        assertThat(skills).extracting(skill -> skill.value().toLowerCase(Locale.ROOT), OfferFacetDto::offers)
            .containsExactly(tuple("java", 2L), tuple("kafka", 1L), tuple("spring", 1L));
        assertThat(queries.topSkills(found(request), 1)).hasSize(1);
        request.setQ("lyon");
        assertThat(queries.topSkills(found(request), 10)).extracting(OfferFacetDto::offers).containsExactly(1L, 1L);
    }

    private static Specification<JobOffer> found(OfferSearchRequest request) {
        return JobOfferSpecifications.matching(request, null, new OfferMatch.Profile(List.of("Java"), 600, null), NOW);
    }

    private void persist(String externalId, OfferSite site, Consumer<JobOffer> details) {
        JobOffer offer = new JobOffer();
        offer.setSite(site);
        offer.setExternalId(externalId);
        offer.setUrl("https://www.example.com/" + externalId);
        offer.setTitle(externalId);
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        offer.setPublishedAt(NOW.minus(Duration.ofHours(2)));
        offer.setFirstSeenAt(NOW);
        offer.setLastSeenAt(NOW);
        details.accept(offer);
        entityManager.persist(offer);
    }
}
