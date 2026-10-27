package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.OfferApplication;
import dev.swirlit.indezy.model.OfferMention;
import dev.swirlit.indezy.model.OfferPublisherBlock;
import dev.swirlit.indezy.model.OfferSiteExclusion;
import dev.swirlit.indezy.model.enums.ApplicationChannel;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.OfferSuspicion;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.JobOfferSpecifications.Origin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** The feed's filters, views and sorts on a real database. */
@DataJpaTest
@ActiveProfiles("test")
class JobOfferSpecificationsTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private static final Origin PARIS = new Origin(48.8589, 2.347);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JobOfferRepository repository;

    private Freelance alice;

    @BeforeEach
    void setUp() {
        alice = freelance("alice@example.com");
        persist("java", offer -> {
            offer.setTitle("Développeur Java Spring");
            offer.setSearchText("developpeur java spring exemple conseil paris");
            offer.setRateMin(550);
            offer.setRateMax(650);
            offer.setDepartmentCode("75");
            offer.setRegionCode("11");
            offer.setLatitude(48.8566);
            offer.setLongitude(2.3522);
            offer.setWorkMode(WorkMode.HYBRID);
            offer.setRemoteDaysPerWeek(3);
            offer.setDurationMonths(6);
            offer.setStartDate(LocalDate.of(2026, 11, 2));
            offer.setSeniority(Seniority.SENIOR);
            offer.setSkills(List.of("Java", "Spring"));
            offer.setPublishedAt(NOW.minus(Duration.ofHours(1)));
        });
        persist("courbevoie", offer -> {
            offer.setTitle("Data engineer");
            offer.setSearchText("data engineer spark courbevoie");
            offer.setRateMax(700);
            offer.setDepartmentCode("92");
            offer.setRegionCode("11");
            offer.setLatitude(48.8976);
            offer.setLongitude(2.2574);
            offer.setWorkMode(WorkMode.REMOTE);
            offer.setRemoteDaysPerWeek(5);
            offer.setStartAsap(true);
            offer.setSkills(List.of("Spark"));
            offer.setSite(OfferSite.COLLECTIVE);
            offer.setPublishedAt(NOW.minus(Duration.ofHours(5)));
        });
        persist("lyon", offer -> {
            offer.setTitle("Architecte cloud");
            offer.setSearchText("architecte cloud aws lyon");
            offer.setRateMin(450);
            offer.setRateMax(450);
            offer.setDepartmentCode("69");
            offer.setRegionCode("84");
            offer.setLatitude(45.764);
            offer.setLongitude(4.8357);
            offer.setWorkMode(WorkMode.ONSITE);
            offer.setRemoteDaysPerWeek(0);
            offer.setDurationMonths(24);
            offer.setStartDate(LocalDate.of(2027, 3, 1));
            offer.setSeniority(Seniority.JUNIOR);
            offer.setSkills(List.of("AWS", "Java"));
            offer.setPublishedAt(NOW.minus(Duration.ofDays(4)));
        });
        persist("unknown", offer -> {
            offer.setTitle("Consultant SAP");
            offer.setSearchText("consultant sap");
            offer.setWorkMode(WorkMode.HYBRID);
            offer.setPublishedAt(NOW.minus(Duration.ofDays(10)));
        });
        persist("expired", offer -> {
            offer.setTitle("Mission terminée");
            offer.setSearchText("mission terminee");
            offer.setExpiresAt(NOW.minus(Duration.ofDays(1)));
            offer.setPublishedAt(NOW.minus(Duration.ofDays(20)));
        });
    }

    @Test
    void listsOpenOffersNewestFirst() {
        assertThat(search(request(), null)).containsExactly("java", "courbevoie", "lyon", "unknown");
    }

    @Test
    void readsAPageWithoutCountingInTheSameOrderAsACountedOne() {
        for (OfferSearchRequest.Sort sort : OfferSearchRequest.Sort.values()) {
            OfferSearchRequest request = request(r -> r.setSort(sort));
            Specification<JobOffer> matching = JobOfferSpecifications.matching(request, null, OfferMatch.Profile.at(PARIS), NOW);
            PageRequest second = PageRequest.of(1, 2);

            Page<JobOffer> counted = repository.findAll(matching, second);
            Page<JobOffer> uncounted = repository.findPage(matching, second, counted.getTotalElements());

            assertThat(uncounted.getContent()).as(sort.name()).containsExactlyElementsOf(counted.getContent());
            assertThat(uncounted.getTotalElements()).as(sort.name()).isEqualTo(4);
            assertThat(uncounted.getTotalPages()).as(sort.name()).isEqualTo(2);
        }
    }

    @Test
    void searchesEveryWordWithoutAccents() {
        assertThat(search(request(r -> r.setQ("DÉVELOPPEUR  java")), null)).containsExactly("java");
        assertThat(search(request(r -> r.setQ("spark 100%")), null)).isEmpty();
        assertThat(search(request(r -> r.setQ("a")), null)).hasSize(4);
    }

    @Test
    void filtersOnTheDailyRate() {
        assertThat(search(request(r -> r.setRateMin(600)), null)).containsExactly("java", "courbevoie", "unknown");
        assertThat(search(request(r -> {
            r.setRateMin(600);
            r.setRateKnown(true);
        }), null)).containsExactly("java", "courbevoie");
        assertThat(search(request(r -> r.setRateMax(500)), null)).containsExactly("lyon", "unknown");
    }

    @Test
    void filtersOnRegionsDepartmentsAndDistance() {
        assertThat(search(request(r -> r.setRegions(List.of("84"))), null)).containsExactly("lyon");
        assertThat(search(request(r -> {
            r.setRegions(List.of("84"));
            r.setDepartments(List.of("92"));
        }), null)).containsExactly("courbevoie", "lyon");
        assertThat(search(request(r -> r.setRadiusKm(20)), PARIS)).containsExactly("java", "courbevoie");
    }

    @Test
    void filtersOnWorkModeAndRemoteDays() {
        assertThat(search(request(r -> r.setWorkModes(List.of(WorkMode.REMOTE, WorkMode.ONSITE))), null))
            .containsExactly("courbevoie", "lyon");
        assertThat(search(request(r -> r.setMinRemoteDays(2)), null)).containsExactly("java", "courbevoie");
    }

    @Test
    void keepsOffersThatStateNoDurationStartOrSeniority() {
        assertThat(search(request(r -> r.setDurationMin(12)), null)).containsExactly("courbevoie", "lyon", "unknown");
        assertThat(search(request(r -> r.setDurationMax(12)), null)).containsExactly("java", "courbevoie", "unknown");
        assertThat(search(request(r -> r.setStartBefore(LocalDate.of(2026, 12, 1))), null))
            .containsExactly("java", "courbevoie", "unknown");
        assertThat(search(request(r -> r.setSeniorities(List.of(Seniority.SENIOR))), null))
            .containsExactly("java", "courbevoie", "unknown");
    }

    @Test
    void filtersOnPublicationSitesAndSkills() {
        assertThat(search(request(r -> r.setPublishedWithinDays(1)), null)).containsExactly("java", "courbevoie");
        assertThat(search(request(r -> r.setSites(List.of(OfferSite.COLLECTIVE))), null)).containsExactly("courbevoie");
        assertThat(search(request(r -> r.setSkills(List.of("java"))), null)).containsExactly("java", "lyon");
        assertThat(search(request(r -> r.setSkills(List.of("JAVA", "aws", " "))), null)).containsExactly("lyon");
    }

    @Test
    void showsTheWorkspacesSavedAndHiddenOffers() {
        state("java", true, false, null);
        state("lyon", false, true, NOW);
        state("courbevoie", false, false, NOW);
        Freelance bob = freelance("bob@example.com");

        assertThat(search(request(), alice.getId())).containsExactly("java", "courbevoie", "unknown");
        assertThat(search(request(), bob.getId())).containsExactly("java", "courbevoie", "lyon", "unknown");
        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.SAVED)), alice.getId())).containsExactly("java");
        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.HIDDEN)), alice.getId())).containsExactly("lyon");
        assertThat(search(request(r -> r.setUnseenOnly(true)), alice.getId())).containsExactly("java", "unknown");
        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.SAVED)), null)).isEmpty();
    }

    @Test
    void listsEachMissionOnceAtItsEarliestCopyTheSearchFinds() {
        groupJavaWithAnEarlierCopy();

        assertThat(search(request(), null)).containsExactly("java-copy", "courbevoie", "lyon", "unknown");
        // The earlier copy names no rate high enough: the later one stands for the mission.
        assertThat(search(request(r -> {
            r.setRateMin(600);
            r.setRateKnown(true);
        }), null)).containsExactly("java", "courbevoie");
        assertThat(search(request(r -> r.setSites(List.of(OfferSite.FREE_WORK))), null)).containsExactly("java", "lyon", "unknown");
        Page<JobOffer> page = repository.findAll(JobOfferSpecifications.matching(request(), null, OfferMatch.Profile.NONE, NOW), PageRequest.of(0, 2));
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    void sharesWhatTheWorkspaceDidAcrossCopies() {
        groupJavaWithAnEarlierCopy();
        state("java", true, false, NOW);

        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.SAVED)), alice.getId())).containsExactly("java-copy");
        assertThat(search(request(r -> r.setUnseenOnly(true)), alice.getId())).containsExactly("courbevoie", "lyon", "unknown");
        state("java-copy", false, true, null);
        assertThat(search(request(), alice.getId())).containsExactly("courbevoie", "lyon", "unknown");
        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.HIDDEN)), alice.getId())).containsExactly("java-copy");
    }

    @Test
    void leavesTheSitesAWorkspaceExcludesOutOfItsFeedOnly() {
        groupJavaWithAnEarlierCopy();
        state("courbevoie", true, false, null);
        OfferSiteExclusion exclusion = new OfferSiteExclusion();
        exclusion.setFreelance(alice);
        exclusion.setSite(OfferSite.COLLECTIVE);
        entityManager.persistAndFlush(exclusion);

        // The Collective copy no longer stands for the mission: the Free-Work one does.
        assertThat(search(request(), alice.getId())).containsExactly("java", "lyon", "unknown");
        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.SAVED)), alice.getId())).containsExactly("courbevoie");
        assertThat(search(request(), null)).containsExactly("java-copy", "courbevoie", "lyon", "unknown");
    }

    @Test
    void leavesTheOffersOfPublishersAWorkspaceBlockedOutOfItsFeedOnly() {
        Map<String, String> publishers = Map.of("java", "Exemple Conseil", "lyon", "EXEMPLE-conseil", "courbevoie", "Données & Cie");
        repository.findAll().forEach(offer -> offer.setCompany(publishers.get(offer.getExternalId())));
        state("lyon", true, false, null);
        OfferPublisherBlock block = new OfferPublisherBlock();
        block.setFreelance(alice);
        block.setName("Exemple Conseil");
        block.setPublisherKey("exemple conseil");
        entityManager.persistAndFlush(block);

        // Both spellings of the publisher are left out.
        assertThat(search(request(), alice.getId())).containsExactly("courbevoie", "unknown");
        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.SAVED)), alice.getId())).containsExactly("lyon");
        assertThat(search(request(), null)).containsExactly("java", "courbevoie", "lyon", "unknown");
    }

    @Test
    void listsTheOffersTheWorkspaceAppliedTo() {
        groupJavaWithAnEarlierCopy();
        JobOffer lyon = repository.findAll().stream().filter(offer -> offer.getExternalId().equals("lyon")).findFirst().orElseThrow();
        JobOfferState applied = new JobOfferState();
        applied.setFreelance(alice);
        applied.setOffer(lyon);
        applied.setApplication(new OfferApplication(LocalDate.of(2026, 10, 18), ApplicationChannel.SITE_FORM));
        entityManager.persistAndFlush(applied);
        Freelance bob = freelance("bob@example.com");

        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.APPLIED)), alice.getId())).containsExactly("lyon");
        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.APPLIED)), bob.getId())).isEmpty();
        assertThat(search(request(r -> r.setView(OfferSearchRequest.View.APPLIED)), null)).isEmpty();
        assertThat(search(request(), alice.getId())).contains("lyon");
    }

    @Test
    void findsTheOffersAWorkspacesNoteNamesWithTheKeywords() {
        groupJavaWithAnEarlierCopy();
        JobOffer lyon = repository.findAll().stream().filter(offer -> offer.getExternalId().equals("lyon")).findFirst().orElseThrow();
        JobOfferState noted = new JobOfferState();
        noted.setFreelance(alice);
        noted.setOffer(lyon);
        noted.setNote("Rappeler Léa, ESN sérieuse");
        entityManager.persistAndFlush(noted);

        assertThat(search(request(r -> r.setQ("lea serieuse")), alice.getId())).containsExactly("lyon");
        assertThat(search(request(r -> r.setQ("lea")), null)).isEmpty();
        assertThat(search(request(r -> r.setQ("java")), alice.getId())).containsExactly("java-copy");
    }

    @Test
    void sortsByRateWithUnknownRatesLastAndByDistance() {
        assertThat(search(request(r -> r.setSort(OfferSearchRequest.Sort.RATE)), null))
            .containsExactly("courbevoie", "java", "lyon", "unknown");
        assertThat(search(request(r -> r.setSort(OfferSearchRequest.Sort.DISTANCE)), PARIS))
            .containsExactly("java", "courbevoie", "lyon", "unknown");
        assertThat(search(request(r -> r.setSort(OfferSearchRequest.Sort.DISTANCE)), null))
            .containsExactly("java", "courbevoie", "lyon", "unknown");
    }

    @Test
    void sortsAndFiltersOnHowWellOffersMatchTheProfile() {
        OfferMatch.Profile profile = new OfferMatch.Profile(List.of("Java", "Spring", "AWS"), 600, PARIS);

        // 80 (two skills, rate met, in Paris), 40 (rate met, remote), 20 (AWS only), 15 (rate and place not stated).
        assertThat(search(request(r -> r.setSort(OfferSearchRequest.Sort.MATCH)), profile))
            .containsExactly("java", "courbevoie", "lyon", "unknown");
        assertThat(search(request(r -> r.setMatchesProfile(true)), profile)).containsExactly("java");
        // A profile without skills scores nothing: the filter and the sort are left out.
        assertThat(search(request(r -> {
            r.setMatchesProfile(true);
            r.setSort(OfferSearchRequest.Sort.MATCH);
        }), OfferMatch.Profile.at(PARIS))).containsExactly("java", "courbevoie", "lyon", "unknown");
    }

    @Test
    void scoresOffersInTheDatabaseAsTheCardsDo() {
        persist("go", offer -> {
            offer.setTitle("Développeur Go");
            offer.setSearchText("developpeur go kubernetes compiegne. spark");
            offer.setSkills(List.of("Go", "Kubernetes"));
            offer.setRateMax(540);
            offer.setLatitude(49.4175);
            offer.setLongitude(2.8261);
            offer.setWorkMode(WorkMode.HYBRID);
            offer.setRemoteDaysPerWeek(3);
            offer.setPublishedAt(NOW.minus(Duration.ofHours(2)));
            offer.setStartDate(LocalDate.of(2026, 9, 1));
        });
        // Every way an offer states its start: as soon as possible, a day after, a day two weeks before, none.
        Map<String, Consumer<JobOffer>> starts = Map.of(
            "java", offer -> offer.setStartAsap(true),
            "courbevoie", offer -> offer.setStartDate(LocalDate.of(2026, 12, 1)),
            "lyon", offer -> offer.setStartDate(LocalDate.of(2026, 11, 20)));
        repository.findAll().forEach(offer -> starts.getOrDefault(offer.getExternalId(), ignored -> { }).accept(offer));
        repository.flush();
        LocalDate today = LocalDate.of(2026, 10, 19);
        List<OfferMatch.Profile> profiles = List.of(
            new OfferMatch.Profile(List.of("Java", "Spring", "AWS"), 600, PARIS),
            new OfferMatch.Profile(List.of("Go", "Kubernetes", "Spark", "SAP"), 600, new Origin(45.764, 4.8357)),
            new OfferMatch.Profile(List.of("sap", "Cloud"), null, null),
            new OfferMatch.Profile(List.of("Spark"), 700, null),
            new OfferMatch.Profile(List.of("Java", "Spring", "AWS"), 600, PARIS, LocalDate.of(2026, 12, 1), today),
            new OfferMatch.Profile(List.of("Java", "Go"), null, null, LocalDate.of(2026, 10, 1), today),
            new OfferMatch.Profile(List.of("Spark", "Java"), 700, null, LocalDate.of(2026, 11, 2), today));
        for (OfferMatch.Profile profile : profiles) {
            Map<String, Integer> scores = new HashMap<>();
            List<JobOffer> open = repository.findAll().stream().filter(offer -> offer.getExpiresAt() == null).toList();
            open.forEach(offer -> scores.put(offer.getExternalId(), OfferMatch.score(offer, offer.getSkills(), profile).score()));
            List<String> byScore = open.stream()
                .sorted(Comparator.comparing((JobOffer offer) -> scores.get(offer.getExternalId())).reversed()
                    .thenComparing(JobOffer::getPublishedAt, Comparator.reverseOrder()))
                .map(JobOffer::getExternalId)
                .toList();

            assertThat(search(request(r -> r.setSort(OfferSearchRequest.Sort.MATCH)), profile)).as("%s", profile).isEqualTo(byScore);
            assertThat(search(request(r -> r.setMatchesProfile(true)), profile)).as("%s", profile)
                .containsExactlyInAnyOrderElementsOf(scores.entrySet().stream()
                    .filter(entry -> entry.getValue() >= OfferMatch.GOOD_MATCH).map(Map.Entry::getKey).toList());
        }
    }

    @Test
    void scoresOffersFromTheirRecordedMentionsAsFromTheirText() {
        List<OfferMatch.Profile> profiles = List.of(
            new OfferMatch.Profile(List.of("Java", "Spring", "AWS"), 600, PARIS),
            new OfferMatch.Profile(List.of("Kubernetes", "Spark", "Cloud"), null, null),
            new OfferMatch.Profile(List.of("JS", "Node.js", "Java"), 700, null));
        // Every offer's mentions recorded as the indexer does, but Lyon's, matched on its text as a new offer is.
        List<String> terms = profiles.stream().flatMap(profile -> profile.terms().stream()).distinct().toList();
        for (JobOffer offer : repository.findAll()) {
            if (offer.getExternalId().equals("lyon")) {
                continue;
            }
            terms.stream().filter(term -> OfferMatch.mentions(offer.getSearchText(), offer.getSkills(), term))
                .forEach(term -> entityManager.persist(new OfferMention(offer.getId(), term)));
            entityManager.getEntityManager().createNativeQuery("UPDATE job_offers SET mentions_indexed_at = CURRENT_TIMESTAMP WHERE id = ?")
                .setParameter(1, offer.getId()).executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();

        for (OfferMatch.Profile plain : profiles) {
            OfferMatch.Profile profile = plain.recording(Set.copyOf(terms));
            List<JobOffer> open = repository.findAll().stream().filter(offer -> offer.getExpiresAt() == null).toList();
            Map<String, Integer> scores = new HashMap<>();
            open.forEach(offer -> scores.put(offer.getExternalId(), OfferMatch.score(offer, offer.getSkills(), profile).score()));
            List<String> byScore = open.stream()
                .sorted(Comparator.comparing((JobOffer offer) -> scores.get(offer.getExternalId())).reversed()
                    .thenComparing(JobOffer::getPublishedAt, Comparator.reverseOrder()))
                .map(JobOffer::getExternalId)
                .toList();

            assertThat(search(request(r -> r.setSort(OfferSearchRequest.Sort.MATCH)), profile)).as("%s", profile).isEqualTo(byScore);
            assertThat(search(request(r -> r.setMatchesProfile(true)), profile)).as("%s", profile)
                .containsExactlyInAnyOrderElementsOf(scores.entrySet().stream()
                    .filter(entry -> entry.getValue() >= OfferMatch.GOOD_MATCH).map(Map.Entry::getKey).toList());
        }
        assertThat(repository.findAll()).filteredOn(offer -> offer.getMentionsIndexedAt() == null).extracting(JobOffer::getExternalId)
            .containsExactly("lyon");
    }

    @Test
    void hidesTheOffersThatLookSuspiciousOnRequest() {
        JobOffer lyon = repository.findAll().stream().filter(offer -> offer.getExternalId().equals("lyon")).findFirst().orElseThrow();
        lyon.setSuspicion(OfferSuspicion.SAME_TEXT_MANY_PUBLISHERS.bit());
        repository.flush();

        assertThat(search(request(r -> r.setHideSuspicious(true)), null)).containsExactly("java", "courbevoie", "unknown");
        assertThat(search(request(), null)).contains("lyon");
    }

    @Test
    void pagesAndCountsTheResults() {
        Page<JobOffer> page = repository.findAll(JobOfferSpecifications.matching(request(), null, OfferMatch.Profile.NONE, NOW), PageRequest.of(1, 3));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(JobOffer::getExternalId).containsExactly("unknown");
    }

    @Test
    void splitsSearchWords() {
        assertThat(JobOfferSpecifications.keywords(null)).isEmpty();
        assertThat(JobOfferSpecifications.keywords("  ")).isEmpty();
        assertThat(JobOfferSpecifications.keywords("Java, java; C++ a b c d e f g h i j")).containsExactly("java", "c++");
    }

    private List<String> search(OfferSearchRequest request, Object context) {
        Long freelanceId = context instanceof Long id ? id : null;
        OfferMatch.Profile profile = context instanceof Origin place ? OfferMatch.Profile.at(place) :
            context instanceof OfferMatch.Profile given ? given : OfferMatch.Profile.NONE;
        return repository.findAll(JobOfferSpecifications.matching(request, freelanceId, profile, NOW), PageRequest.of(0, 20))
            .getContent().stream().map(JobOffer::getExternalId).toList();
    }

    private static OfferSearchRequest request() {
        return new OfferSearchRequest();
    }

    private static OfferSearchRequest request(Consumer<OfferSearchRequest> filters) {
        OfferSearchRequest request = new OfferSearchRequest();
        filters.accept(request);
        return request;
    }

    private void persist(String externalId, Consumer<JobOffer> details) {
        JobOffer offer = new JobOffer();
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId(externalId);
        offer.setUrl("https://www.example.com/" + externalId);
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        offer.setFirstSeenAt(NOW);
        offer.setLastSeenAt(NOW);
        details.accept(offer);
        entityManager.persist(offer);
    }

    /** The Java offer, also on Collective an hour earlier with a lower rate. */
    private void groupJavaWithAnEarlierCopy() {
        persist("java-copy", offer -> {
            offer.setSite(OfferSite.COLLECTIVE);
            offer.setTitle("Développeur Java Spring");
            offer.setSearchText("developpeur java spring exemple conseil paris");
            offer.setRateMax(500);
            offer.setDepartmentCode("75");
            offer.setPublishedAt(NOW.minus(Duration.ofHours(2)));
            offer.setCopyGroup(900L);
        });
        repository.findAll().stream().filter(o -> o.getExternalId().equals("java")).forEach(o -> o.setCopyGroup(900L));
        entityManager.flush();
    }

    private void state(String externalId, boolean saved, boolean hidden, Instant seenAt) {
        JobOffer offer = repository.findAll().stream().filter(o -> o.getExternalId().equals(externalId)).findFirst().orElseThrow();
        JobOfferState state = new JobOfferState();
        state.setFreelance(alice);
        state.setOffer(offer);
        state.setSaved(saved);
        state.setHidden(hidden);
        state.setSeenAt(seenAt);
        entityManager.persist(state);
    }

    private Freelance freelance(String email) {
        Freelance freelance = new Freelance();
        freelance.setFirstName("Test");
        freelance.setLastName("User");
        freelance.setEmail(email);
        freelance.setStatus(EmploymentStatus.FREELANCE);
        return entityManager.persistAndFlush(freelance);
    }
}
