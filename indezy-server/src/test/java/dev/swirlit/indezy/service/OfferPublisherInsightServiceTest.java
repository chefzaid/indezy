package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferPublisherDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferPublisherBlock;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferPublisherBlockRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferPublisherInsightServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final ClientRepository clients = mock(ClientRepository.class);
    private final OfferPublisherBlockRepository blocks = mock(OfferPublisherBlockRepository.class);
    private final OfferPublisherInsightService service = new OfferPublisherInsightService(offers, clients, blocks,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void describesWhatAPublisherPutsInTheFeed() {
        // Two open missions (one on two sites), one published again under a new reference, one expired, one too old to count.
        JobOffer first = rated(offer(1L, "Exemple Conseil", OfferSite.FREE_WORK, "dev-java", 10), 550, 650);
        first.setCopyGroup(70L);
        JobOffer copy = rated(offer(2L, "EXEMPLE-conseil", OfferSite.COLLECTIVE, "dev-java", 8), 600, null);
        copy.setCopyGroup(70L);
        JobOffer again = expired(offer(3L, "Exemple Conseil", OfferSite.FREE_WORK, "dev-java", 40));
        JobOffer cloud = rated(offer(4L, "Exemple Conseil", OfferSite.FREE_WORK, "cloud", 30), 700, null);
        JobOffer old = offer(5L, "Exemple Conseil", OfferSite.FREE_WORK, "sap", 120);
        when(offers.findByCompanyKeyOrderByPublishedAtDesc("exemple conseil")).thenReturn(List.of(copy, first, cloud, again, old));
        Client esn = new Client();
        esn.setId(12L);
        esn.setCompanyName("Exemple conseil");
        esn.setIsFinal(false);
        Client other = new Client();
        other.setId(13L);
        other.setCompanyName("Airbus");
        when(clients.findByFreelanceId(3L)).thenReturn(List.of(other, esn));
        OfferPublisherBlock block = new OfferPublisherBlock();
        block.setId(9L);
        when(blocks.findByFreelanceIdAndPublisherKey(3L, "exemple conseil")).thenReturn(Optional.of(block));

        OfferPublisherDto publisher = service.insights(3L, " exemple-CONSEIL ");

        assertThat(publisher.key()).isEqualTo("exemple conseil");
        assertThat(publisher.name()).isEqualTo("Exemple Conseil");
        assertThat(publisher.openMissions()).isEqualTo(3);
        // Open offers: 600 (550–650), 600 and 700.
        assertThat(publisher.medianRate()).isEqualTo(600);
        // Open for 8, 10, 30 and 120 days.
        assertThat(publisher.medianOpenDays()).isEqualTo(20);
        // Within 90 days: dev-java on Free-Work (twice), on Collective, and cloud.
        assertThat(publisher.missions()).isEqualTo(3);
        assertThat(publisher.republished()).isEqualTo(1);
        assertThat(publisher.sites()).extracting(OfferPublisherDto.SiteOffers::siteName, OfferPublisherDto.SiteOffers::offers)
            .containsExactly(Tuple.tuple("Free-Work", 3), Tuple.tuple("Collective", 1));
        assertThat(publisher.client()).isEqualTo(new OfferPublisherDto.Client(12L, "Exemple conseil", true));
        assertThat(publisher.blockId()).isEqualTo(9L);
        assertThat(publisher.offers()).extracting(OfferPublisherDto.Summary::id).containsExactly(2L, 1L, 4L, 5L);
    }

    @Test
    void refusesAPublisherWithoutOffersOrWords() {
        assertThatThrownBy(() -> service.insights(3L, "Personne")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.insights(3L, " -- ")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void leavesOutRatesAndAgesItsOffersDoNotHave() {
        when(offers.findByCompanyKeyOrderByPublishedAtDesc("acme"))
            .thenReturn(List.of(expired(offer(1L, "Acme", OfferSite.YEETS, null, 5))));

        OfferPublisherDto publisher = service.insights(3L, "Acme");

        assertThat(publisher.openMissions()).isZero();
        assertThat(publisher.medianRate()).isNull();
        assertThat(publisher.medianOpenDays()).isNull();
        assertThat(publisher.missions()).isZero();
        assertThat(publisher.client()).isNull();
        assertThat(publisher.blockId()).isNull();
        assertThat(publisher.offers()).isEmpty();
    }

    private static JobOffer offer(Long id, String company, OfferSite site, String mission, int daysAgo) {
        JobOffer offer = new JobOffer();
        offer.setId(id);
        offer.setCompany(company);
        offer.setSite(site);
        offer.setExternalId("ref-" + id);
        offer.setPublisherKey(mission);
        offer.setTitle("Mission " + id);
        offer.setPublishedAt(NOW.minus(Duration.ofDays(daysAgo)));
        return offer;
    }

    private static JobOffer rated(JobOffer offer, Integer rateMin, Integer rateMax) {
        offer.setRateMin(rateMin);
        offer.setRateMax(rateMax);
        return offer;
    }

    private static JobOffer expired(JobOffer offer) {
        offer.setExpiresAt(NOW.minusSeconds(60));
        return offer;
    }
}
