package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.OfferPublisherBlock;
import dev.swirlit.indezy.model.OfferSiteExclusion;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.OfferSkip;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The offer queries the feed, the collection and the retention rely on. */
@DataJpaTest
@ActiveProfiles("test")
class JobOfferRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JobOfferRepository offers;

    @Autowired
    private JobOfferStateRepository states;

    @Autowired
    private OfferSiteStatusRepository statuses;

    @Autowired
    private OfferSkipRepository skips;

    @Autowired
    private OfferSiteExclusionRepository exclusions;

    @Autowired
    private OfferPublisherBlockRepository blocks;

    private Freelance freelance;
    private JobOffer recent;
    private JobOffer old;
    private JobOffer expired;

    @BeforeEach
    void setUp() {
        freelance = new Freelance();
        freelance.setFirstName("Alice");
        freelance.setLastName("Martin");
        freelance.setEmail("alice@example.com");
        freelance.setStatus(EmploymentStatus.FREELANCE);
        entityManager.persist(freelance);
        recent = offer(OfferSite.FREE_WORK, "1", NOW.minus(Duration.ofDays(1)), null, List.of("Java", "Spring", "Kafka"));
        old = offer(OfferSite.FREE_WORK, "2", NOW.minus(Duration.ofDays(120)), null, List.of("java"));
        expired = offer(OfferSite.YEETS, "3", NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(1)), List.of("Jakarta"));
        entityManager.flush();
    }

    @Test
    void findsOffersOfASiteByTheirIds() {
        assertThat(offers.findBySiteAndExternalIdIn(OfferSite.FREE_WORK, List.of("1", "3", "9")))
            .extracting(JobOffer::getExternalId).containsExactly("1");
    }

    @Test
    void namesTheKnownIdsOfASiteAndTheSkippedOnes() {
        assertThat(offers.findKnownIds(OfferSite.FREE_WORK, List.of("1", "2", "3", "9"))).containsExactlyInAnyOrder("1", "2");
        skips.saveAndFlush(new OfferSkip(OfferSite.WORKDISPO, "cdi-1", NOW.minus(Duration.ofDays(40))));
        skips.saveAndFlush(new OfferSkip(OfferSite.WORKDISPO, "cdi-2", NOW));

        assertThat(skips.findSkippedIds(OfferSite.WORKDISPO, List.of("cdi-1", "cdi-2", "cdi-3"))).containsExactlyInAnyOrder("cdi-1", "cdi-2");
        assertThat(skips.deleteSkippedBefore(NOW.minus(Duration.ofDays(30)))).isEqualTo(1);
        assertThat(skips.findSkippedIds(OfferSite.WORKDISPO, List.of("cdi-1", "cdi-2"))).containsExactly("cdi-2");
    }

    @Test
    void findsCopiesOnOtherSitesAndTheirGroups() {
        recent.setPublisherKey("publisher");
        old.setOpeningKey("opening");
        expired.setPublisherKey("publisher");
        expired.setCopyGroup(recent.getId());
        recent.setCopyGroup(recent.getId());
        entityManager.flush();

        assertThat(offers.findCopyCandidates(OfferSite.COLLECTIVE, "publisher", "opening")).extracting(JobOffer::getExternalId)
            .containsExactlyInAnyOrder("1", "2", "3");
        assertThat(offers.findCopyCandidates(OfferSite.FREE_WORK, "publisher", null)).extracting(JobOffer::getExternalId)
            .containsExactly("3");
        assertThat(offers.findCopyGroupOf(recent)).extracting(JobOffer::getExternalId).containsExactlyInAnyOrder("1", "3");
        assertThat(offers.findCopyGroupOf(old)).containsExactly(old);
    }

    @Test
    void readsSkillsInTheirOrder() {
        List<Object[]> rows = offers.findSkillsOf(List.of(recent.getId(), expired.getId()));

        assertThat(rows).extracting(row -> row[1]).containsExactly("Java", "Spring", "Kafka", "Jakarta");
        assertThat(rows.get(0)[0]).isEqualTo(recent.getId());
    }

    @Test
    void countsOpenOffersPerSiteAndRanksTheirSkills() {
        assertThat(offers.countOpenBySite(NOW)).singleElement()
            .satisfies(row -> assertThat(row).containsExactly(OfferSite.FREE_WORK, 2L));

        List<Object[]> skills = offers.findTopSkills(NOW, "ja%", PageRequest.of(0, 5));

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0)[1]).isEqualTo(2L);
        assertThat(((String) skills.get(0)[0]).toLowerCase()).isEqualTo("java");
    }

    @Test
    void disposesOfOldAndExpiredOffersNobodyKept() {
        state(old, true, null);

        List<Long> disposable = offers.findDisposableIds(NOW.minus(Duration.ofDays(90)), NOW);

        assertThat(disposable).containsExactly(expired.getId());

        states.deleteByOfferIdIn(disposable);
        offers.deleteSkillsOf(disposable);
        assertThat(offers.deleteByIdIn(disposable)).isEqualTo(1);
        entityManager.clear();
        assertThat(offers.findAll()).extracting(JobOffer::getExternalId).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    void projectsTheWorkspacesStatesAndForgetsDeletedProjects() {
        Project project = project();
        state(recent, false, project);
        JobOfferState hidden = state(old, false, null);
        hidden.setHidden(true);
        hidden.setSeenAt(NOW);
        entityManager.flush();

        List<OfferStateDto> found = states.findStates(freelance.getId(), List.of(recent.getId(), old.getId(), expired.getId()));

        assertThat(found).containsExactlyInAnyOrder(
            new OfferStateDto(recent.getId(), false, false, false, project.getId()),
            new OfferStateDto(old.getId(), false, true, true, null));
        assertThat(states.findByFreelanceIdAndOfferId(freelance.getId(), old.getId())).isPresent();
        assertThat(states.findByFreelanceId(freelance.getId())).hasSize(2);

        assertThat(states.clearImportedProject(project.getId())).isEqualTo(1);
        entityManager.clear();
        assertThat(states.findStates(freelance.getId(), List.of(recent.getId())).get(0).importedProjectId()).isNull();
    }

    @Test
    void letsOneRunClaimADueSite() {
        statuses.saveAndFlush(new OfferSiteStatus(OfferSite.LEHIBOU, NOW.minusSeconds(60)));
        Instant stale = NOW.minus(Duration.ofHours(1));

        assertThat(statuses.claim(OfferSite.LEHIBOU, NOW, stale)).isEqualTo(1);
        assertThat(statuses.claim(OfferSite.LEHIBOU, NOW, stale)).isZero();
        assertThat(statuses.claim(OfferSite.LEHIBOU, NOW.plus(Duration.ofHours(2)), NOW.plusSeconds(1))).isEqualTo(1);
        assertThat(statuses.claim(OfferSite.YEETS, NOW, stale)).isZero();
    }

    @Test
    void countsWhatEachSiteBroughtOverADayAndAWeek() {
        List<Object[]> rows = offers.collectionBySite(NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(7)));

        assertThat(rows).extracting(row -> row[0], row -> ((Number) row[1]).longValue(), row -> ((Number) row[2]).longValue(), row -> row[3])
            .containsExactlyInAnyOrder(
                tuple(OfferSite.FREE_WORK, 1L, 1L, NOW.minus(Duration.ofDays(1))),
                tuple(OfferSite.YEETS, 0L, 1L, NOW.minus(Duration.ofDays(5))));
    }

    @Test
    void readsASiteOnRequestOnceEveryQuarterOfAnHour() {
        OfferSiteStatus status = new OfferSiteStatus(OfferSite.LEHIBOU, NOW.plus(Duration.ofHours(1)));
        status.setLastRunAt(NOW.minus(Duration.ofMinutes(20)));
        statuses.saveAndFlush(status);
        Instant stale = NOW.minus(Duration.ofHours(1));

        assertThat(statuses.requestRefresh(OfferSite.LEHIBOU, NOW, NOW.minus(Duration.ofMinutes(15)), stale)).isEqualTo(1);
        entityManager.clear();
        OfferSiteStatus requested = statuses.findById(OfferSite.LEHIBOU).orElseThrow();
        assertThat(requested.getNextRunAt()).isEqualTo(NOW);
        assertThat(requested.getRefreshRequestedAt()).isEqualTo(NOW);
        // Asked for again too soon, or while a run holds it.
        Instant later = NOW.plus(Duration.ofMinutes(10));
        assertThat(statuses.requestRefresh(OfferSite.LEHIBOU, later, later.minus(Duration.ofMinutes(15)), stale)).isZero();
        assertThat(statuses.claim(OfferSite.LEHIBOU, later, stale)).isEqualTo(1);
        Instant next = NOW.plus(Duration.ofMinutes(20));
        assertThat(statuses.requestRefresh(OfferSite.LEHIBOU, next, next.minus(Duration.ofMinutes(15)), stale)).isZero();
        assertThat(statuses.requestRefresh(OfferSite.LEHIBOU, next, next.minus(Duration.ofMinutes(15)), later.plusSeconds(1))).isEqualTo(1);
        assertThat(statuses.requestRefresh(OfferSite.YEETS, NOW, NOW, stale)).isZero();
    }

    @Test
    void keepsEachWorkspacesExcludedSites() {
        exclusion(OfferSite.YEETS);
        exclusion(OfferSite.COLLECTIVE);

        assertThat(exclusions.findSitesByFreelanceId(freelance.getId())).containsExactly(OfferSite.COLLECTIVE, OfferSite.YEETS);
        assertThat(exclusions.existsByFreelanceIdAndSite(freelance.getId(), OfferSite.YEETS)).isTrue();
        assertThat(exclusions.deleteByFreelanceIdAndSite(freelance.getId(), OfferSite.YEETS)).isEqualTo(1);
        assertThat(exclusions.existsByFreelanceIdAndSite(freelance.getId(), OfferSite.YEETS)).isFalse();
        assertThat(exclusions.findSitesByFreelanceId(freelance.getId() + 1)).isEmpty();
    }

    @Test
    void findsTheOffersStoredBeforePublishersHadAKey() {
        recent.setCompany("Celad");
        old.setCompany("Exemple Conseil");
        entityManager.flush();
        entityManager.getEntityManager().createNativeQuery("update job_offers set company_key = null").executeUpdate();
        entityManager.clear();

        assertThat(offers.findByCompanyIsNotNullAndCompanyKeyIsNullAndIdGreaterThanOrderById(0L, PageRequest.of(0, 10)))
            .extracting(JobOffer::getExternalId).containsExactly("1", "2");
        assertThat(offers.findByCompanyIsNotNullAndCompanyKeyIsNullAndIdGreaterThanOrderById(recent.getId(), PageRequest.of(0, 10)))
            .extracting(JobOffer::getExternalId).containsExactly("2");
    }

    @Test
    void keepsEachWorkspacesBlockedPublishers() {
        OfferPublisherBlock block = new OfferPublisherBlock();
        block.setFreelance(freelance);
        block.setName("Exemple Conseil");
        block.setPublisherKey("exemple conseil");
        entityManager.persistAndFlush(block);

        assertThat(blocks.findByFreelanceIdAndPublisherKey(freelance.getId(), "exemple conseil")).isPresent();
        assertThat(blocks.findByIdAndFreelanceId(block.getId(), freelance.getId() + 1)).isEmpty();
        assertThat(blocks.findByFreelanceIdOrderByNameAsc(freelance.getId())).extracting(OfferPublisherBlock::getName)
            .containsExactly("Exemple Conseil");
        assertThat(blocks.countByFreelanceId(freelance.getId())).isEqualTo(1);
    }

    private void exclusion(OfferSite site) {
        OfferSiteExclusion exclusion = new OfferSiteExclusion();
        exclusion.setFreelance(freelance);
        exclusion.setSite(site);
        entityManager.persistAndFlush(exclusion);
    }

    private JobOffer offer(OfferSite site, String externalId, Instant publishedAt, Instant expiresAt, List<String> skills) {
        JobOffer offer = new JobOffer();
        offer.setSite(site);
        offer.setExternalId(externalId);
        offer.setUrl("https://www.example.com/" + externalId);
        offer.setTitle("Offre " + externalId);
        offer.setPublishedAt(publishedAt);
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        offer.setFirstSeenAt(publishedAt);
        offer.setLastSeenAt(publishedAt);
        offer.setExpiresAt(expiresAt);
        offer.setSkills(skills);
        return entityManager.persist(offer);
    }

    private JobOfferState state(JobOffer offer, boolean saved, Project project) {
        JobOfferState state = new JobOfferState();
        state.setFreelance(freelance);
        state.setOffer(offer);
        state.setSaved(saved);
        state.setImportedProject(project);
        return entityManager.persistAndFlush(state);
    }

    private Project project() {
        Client client = new Client();
        client.setCompanyName("Agence Exemple");
        client.setCity("Paris");
        client.setIsFinal(false);
        client.setFreelance(freelance);
        entityManager.persist(client);
        Project project = new Project();
        project.setRole("Développeur Java");
        project.setDailyRate(600);
        project.setClient(client);
        project.setFreelance(freelance);
        return entityManager.persistAndFlush(project);
    }
}
