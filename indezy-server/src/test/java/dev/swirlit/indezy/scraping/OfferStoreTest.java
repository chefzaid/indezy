package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.OfferSkip;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferSkipRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OfferStoreTest {

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final OfferSkipRepository skips = mock(OfferSkipRepository.class);
    private final OfferStore store = new OfferStore(offers, skips);

    @Test
    @SuppressWarnings("unchecked")
    void addsNewOffersAndRefreshesKnownOnes() {
        JobOffer known = offer("1", "Ancien titre", OfferDatePrecision.FIRST_SEEN, Instant.parse("2026-10-01T00:00:00Z"));
        known.setSkills(List.of("Java"));
        when(offers.findBySiteAndExternalIdIn(eq(OfferSite.FREE_WORK), any())).thenReturn(List.of(known));
        JobOffer refreshed = offer("1", "Nouveau titre", OfferDatePrecision.FIRST_SEEN, Instant.parse("2026-10-19T00:00:00Z"));
        refreshed.setSkills(List.of("Java", "Kafka"));
        JobOffer created = offer("2", "Data engineer", OfferDatePrecision.EXACT, Instant.parse("2026-10-19T08:00:00Z"));

        int added = store.save(OfferSite.FREE_WORK, List.of(refreshed, created, offer("2", "Doublon", OfferDatePrecision.EXACT, null)));

        assertThat(added).isEqualTo(1);
        assertThat(known.getTitle()).isEqualTo("Nouveau titre");
        assertThat(known.getSkills()).containsExactly("Java", "Kafka");
        assertThat(known.getPublishedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        ArgumentCaptor<List<JobOffer>> saved = ArgumentCaptor.forClass(List.class);
        verify(offers).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(JobOffer::getTitle).containsExactly("Data engineer");
    }

    @Test
    void movesTheDateOfARepublishedOffer() {
        JobOffer known = offer("1", "Titre", OfferDatePrecision.EXACT, Instant.parse("2026-10-01T00:00:00Z"));
        when(offers.findBySiteAndExternalIdIn(eq(OfferSite.FREE_WORK), any())).thenReturn(List.of(known));

        store.save(OfferSite.FREE_WORK, List.of(offer("1", "Titre", OfferDatePrecision.EXACT, Instant.parse("2026-10-19T00:00:00Z"))));

        assertThat(known.getPublishedAt()).isEqualTo(Instant.parse("2026-10-19T00:00:00Z"));
    }

    @Test
    void groupsANewOfferWithItsCopyOnAnotherSite() {
        JobOffer collected = keyed(offer("2", "Architecte", OfferDatePrecision.EXACT, Instant.parse("2026-10-19T08:00:00Z")), 9L, "75");
        JobOffer copy = keyed(offer("c-5", "Architecte", OfferDatePrecision.EXACT, Instant.parse("2026-10-18T08:00:00Z")), 5L, null);
        copy.setSite(OfferSite.COLLECTIVE);
        when(offers.findCopyCandidates(OfferSite.FREE_WORK, "publisher", "opening")).thenReturn(List.of(copy));

        store.save(OfferSite.FREE_WORK, List.of(collected));

        assertThat(collected.getCopyGroup()).isEqualTo(5L);
        assertThat(copy.getCopyGroup()).isEqualTo(5L);
    }

    @Test
    void mergesTheGroupsOfCopiesAndLeavesOutOffersPlacedElsewhere() {
        JobOffer collected = keyed(offer("2", "Architecte", OfferDatePrecision.EXACT, Instant.parse("2026-10-19T08:00:00Z")), 3L, "69");
        JobOffer lyonElsewhere = keyed(offer("w-5", "Architecte", OfferDatePrecision.EXACT, null), 5L, "75");
        lyonElsewhere.setOpeningKey("other text");
        JobOffer sameText = keyed(offer("w-8", "Architecte", OfferDatePrecision.EXACT, null), 8L, "75");
        sameText.setPublisherKey("other publisher");
        sameText.setCopyGroup(6L);
        JobOffer groupedBefore = keyed(offer("y-6", "Architecte", OfferDatePrecision.EXACT, null), 6L, null);
        groupedBefore.setCopyGroup(6L);
        when(offers.findCopyCandidates(any(), any(), any())).thenReturn(List.of(lyonElsewhere, sameText));
        when(offers.findByCopyGroupIn(Set.of(6L))).thenReturn(List.of(sameText, groupedBefore));

        store.save(OfferSite.FREE_WORK, List.of(collected));

        assertThat(List.of(collected, sameText, groupedBefore)).extracting(JobOffer::getCopyGroup).containsOnly(3L);
        assertThat(lyonElsewhere.getCopyGroup()).isNull();
    }

    @Test
    void givesOffersStoredWithoutKeysTheirKeysAndGroupsThem() {
        JobOffer stored = offer("2", "Architecte cloud", OfferDatePrecision.EXACT, null);
        stored.setId(4L);
        stored.setCompany("Celad");
        JobOffer untitled = offer("3", " ", OfferDatePrecision.EXACT, null);
        when(offers.findByPublisherKeyIsNullOrderById(any())).thenReturn(List.of(stored, untitled));

        assertThat(store.keyStoredOffers()).isEqualTo(1);

        assertThat(stored.getPublisherKey()).isEqualTo(OfferFingerprint.publisherKey(OfferSite.FREE_WORK, "Architecte cloud", "Celad"));
        assertThat(untitled.getPublisherKey()).isNull();
        verify(offers).findCopyCandidates(OfferSite.FREE_WORK, stored.getPublisherKey(), null);
    }

    @Test
    void looksForNoCopiesOfAnOfferWithoutKeys() {
        store.save(OfferSite.FREE_WORK, List.of(offer("2", "Architecte", OfferDatePrecision.EXACT, null)));

        verify(offers, never()).findCopyCandidates(any(), any(), any());
    }

    @Test
    void savesNothingForAnEmptyPage() {
        assertThat(store.save(OfferSite.YEETS, List.of())).isZero();
        verifyNoInteractions(offers);
    }

    @Test
    @SuppressWarnings("unchecked")
    void remembersOffersLeftOutOnceAndOnlyWhenTheyFit() {
        when(skips.findSkippedIds(eq(OfferSite.WORKDISPO), any())).thenReturn(List.of("cdi-1"));
        Instant now = Instant.parse("2026-10-19T10:00:00Z");

        store.skip(OfferSite.WORKDISPO, java.util.Arrays.asList("cdi-1", "cdi-2", null, "x".repeat(200), "cdi-2"), now);
        store.skip(OfferSite.WORKDISPO, List.of(), now);

        ArgumentCaptor<List<OfferSkip>> saved = ArgumentCaptor.forClass(List.class);
        verify(skips).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(OfferSkip::getExternalId).containsExactly("cdi-2");
        assertThat(saved.getValue().get(0).getSkippedAt()).isEqualTo(now);
    }

    @Test
    void knowsStoredAndSkippedOffers() {
        when(offers.findKnownIds(OfferSite.MINDQUEST, List.of("1", "2", "3"))).thenReturn(List.of("1"));
        when(skips.findSkippedIds(OfferSite.MINDQUEST, List.of("1", "2", "3"))).thenReturn(List.of("2"));

        assertThat(store.known(OfferSite.MINDQUEST, List.of("1", "2", "3"))).containsExactlyInAnyOrder("1", "2");
        assertThat(store.known(OfferSite.MINDQUEST, List.of())).isEmpty();
    }

    /** An offer as stored, with its id, keys and department. */
    private static JobOffer keyed(JobOffer offer, Long id, String departmentCode) {
        offer.setId(id);
        offer.setPublisherKey("publisher");
        offer.setOpeningKey("opening");
        offer.setDepartmentCode(departmentCode);
        return offer;
    }

    private static JobOffer offer(String externalId, String title, OfferDatePrecision precision, Instant publishedAt) {
        JobOffer offer = new JobOffer();
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId(externalId);
        offer.setUrl("https://www.free-work.com/" + externalId);
        offer.setTitle(title);
        offer.setDatePrecision(precision);
        offer.setPublishedAt(publishedAt);
        offer.setFirstSeenAt(publishedAt);
        offer.setLastSeenAt(publishedAt);
        return offer;
    }
}
