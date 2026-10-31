package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferCopyDto;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferMatchDto;
import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.dto.OfferPublisherSpreadDto;
import dev.swirlit.indezy.dto.OfferRegionDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSiteDto;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferSiteExclusionRepository;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import dev.swirlit.indezy.scraping.GeoLocator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final JobOfferStateRepository states = mock(JobOfferStateRepository.class);
    private final OfferSiteStatusRepository statuses = mock(OfferSiteStatusRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final GeoLocator geoLocator = mock(GeoLocator.class);
    private final OfferSiteExclusionRepository exclusions = mock(OfferSiteExclusionRepository.class);
    private final OfferRateBenchmarkService benchmarks = mock(OfferRateBenchmarkService.class);
    private final OfferHistoryService history = mock(OfferHistoryService.class);
    private final OfferSearchService service = new OfferSearchService(offers, states, statuses, freelances, geoLocator, exclusions,
        benchmarks, history, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void measuresNearAndReachableByTheProfilesLongestCommute() {
        when(freelances.findProfileCity(3L)).thenReturn(Optional.of("Paris"));
        when(geoLocator.locate("Paris", null)).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", "11", 48.8589, 2.347)));
        assertThat(service.profile(3L).nearKm()).isEqualTo(30);

        when(freelances.findMaxDriveMinutes(3L)).thenReturn(Optional.of(45));
        when(freelances.findMaxTransitMinutes(3L)).thenReturn(Optional.of(60));
        OfferMatch.Profile profile = service.profile(3L);
        assertThat(profile.nearKm()).isEqualTo(Math.max(CommuteService.reachKm(45, TravelMode.DRIVING, true),
            CommuteService.reachKm(60, TravelMode.TRANSIT, true)));
        assertThat(profile.reachableKm()).isEqualTo(Math.max(CommuteService.reachKm(68, TravelMode.DRIVING, true),
            CommuteService.reachKm(90, TravelMode.TRANSIT, true)));

        when(freelances.findMaxTransitMinutes(3L)).thenReturn(Optional.empty());
        assertThat(service.profile(3L).nearKm()).isEqualTo(CommuteService.reachKm(45, TravelMode.DRIVING, true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void estimatesNoTripToAFullyRemoteMission() {
        JobOffer offer = offer(7L);
        offer.setWorkMode(WorkMode.REMOTE);
        when(offers.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(offer), PageRequest.of(0, 20), 1));
        when(offers.findSkillsOf(List.of(7L))).thenReturn(List.of());
        when(freelances.findProfileCity(3L)).thenReturn(Optional.of("Paris"));
        when(geoLocator.locate("Paris", null)).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", "11", 48.8589, 2.347)));

        OfferDto dto = service.search(3L, new OfferSearchRequest()).content().get(0);

        assertThat(dto.distanceKm()).isEqualTo(8);
        assertThat(dto.commute()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void answersAPageWithSkillsStatesAndDistances() {
        JobOffer offer = offer(7L);
        when(offers.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(offer), PageRequest.of(0, 20), 1));
        when(offers.findSkillsOf(List.of(7L))).thenReturn(List.<Object[]>of(new Object[] {7L, "Java"}, new Object[] {7L, "Kafka"}));
        when(states.findStates(3L, List.of(7L))).thenReturn(List.of(new OfferStateDto(7L, true, false, true, 12L)));
        when(freelances.findProfileCity(3L)).thenReturn(Optional.of("Paris"));
        when(geoLocator.locate("Paris", null)).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", "11", 48.8589, 2.347)));

        OfferPageDto page = service.search(3L, new OfferSearchRequest());

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.distanceAvailable()).isTrue();
        OfferDto dto = page.content().get(0);
        assertThat(dto.siteName()).isEqualTo("Free-Work");
        assertThat(dto.skills()).containsExactly("Java", "Kafka");
        assertThat(dto.saved()).isTrue();
        assertThat(dto.seen()).isTrue();
        assertThat(dto.importedProjectId()).isEqualTo(12L);
        assertThat(dto.distanceKm()).isEqualTo(8);
        // About 10 km of road from Paris to La Défense: some 20 minutes by car, Île-de-France's rush hour slowing it.
        assertThat(dto.commute().carMinutes()).isBetween(18, 22);
        assertThat(dto.commute().carRushMinutes()).isGreaterThan(dto.commute().carMinutes());
        assertThat(dto.commute().transitMinutes()).isGreaterThan(dto.commute().carMinutes());
        assertThat(dto.departmentName()).isEqualTo("Hauts-de-Seine");
        assertThat(dto.regionName()).isEqualTo("Île-de-France");
        assertThat(dto.description()).isNull();
        assertThat(dto.excerpt()).isEqualTo("Mission longue à La Défense.");
        assertThat(dto.expired()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void scoresEachOfferAgainstTheProfile() {
        JobOffer offer = offer(7L);
        offer.setSearchText("developpeur java la defense");
        offer.setRateMax(620);
        when(offers.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(offer), PageRequest.of(0, 20), 1));
        when(freelances.findProfileSkills(3L)).thenReturn(List.of("Java", "Kafka"));
        when(freelances.findTargetDailyRate(3L)).thenReturn(Optional.of(600));

        OfferPageDto page = service.search(3L, new OfferSearchRequest());

        assertThat(page.matchAvailable()).isTrue();
        OfferMatchDto match = page.content().get(0).match();
        // One skill of two (30 of 60) and the rate met (25), over 85: no city to measure from.
        assertThat(match.score()).isEqualTo(65);
        assertThat(match.skills()).containsExactly("Java");
        assertThat(match.rate()).isEqualTo(OfferMatchDto.RateFit.MEETS);
        assertThat(match.place()).isNull();
        assertThat(match.availability()).isNull();
        assertThat(service.search(null, new OfferSearchRequest()).matchAvailable()).isFalse();

        // Available from mid-November (today is 19 October in Paris), for a mission starting on 2 November: 5 of 10 more.
        offer.setStartDate(java.time.LocalDate.of(2026, 11, 2));
        when(freelances.findAvailabilityDate(3L)).thenReturn(Optional.of(java.time.LocalDate.of(2026, 11, 16)));
        OfferMatchDto available = service.search(3L, new OfferSearchRequest()).content().get(0).match();
        assertThat(available.availability()).isEqualTo(OfferMatchDto.AvailabilityFit.SOON);
        assertThat(available.score()).isEqualTo(63);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsTheOpenCopiesOnOtherSitesAndSharesTheirState() {
        JobOffer offer = offer(7L);
        offer.setCopyGroup(7L);
        JobOffer collective = copy(8L, OfferSite.COLLECTIVE, NOW.minusSeconds(600), null);
        JobOffer laterOnCollective = copy(9L, OfferSite.COLLECTIVE, NOW, null);
        JobOffer expired = copy(10L, OfferSite.YEETS, NOW.minusSeconds(60), NOW.minusSeconds(1));
        JobOffer sameSite = copy(11L, OfferSite.FREE_WORK, NOW.minusSeconds(60), null);
        JobOffer workDispo = copy(12L, OfferSite.WORKDISPO, NOW.minusSeconds(60), null);
        when(offers.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(offer), PageRequest.of(0, 20), 1));
        when(states.findStates(eq(3L), anyList())).thenReturn(List.of(new OfferStateDto(9L, true, false, false, null)));
        // The workspace keeps Next Mondays out: its copy is not linked.
        JobOffer nextMondays = copy(13L, OfferSite.NEXT_MONDAYS, NOW.minusSeconds(60), null);
        when(offers.findByCopyGroupIn(Set.of(7L)))
            .thenReturn(List.of(offer, workDispo, laterOnCollective, collective, expired, sameSite, nextMondays));
        when(exclusions.findSitesByFreelanceId(3L)).thenReturn(List.of(OfferSite.NEXT_MONDAYS));

        OfferDto dto = service.search(3L, new OfferSearchRequest()).content().get(0);

        assertThat(dto.alsoOn()).extracting(OfferCopyDto::id, OfferCopyDto::siteName)
            .containsExactly(tuple(8L, "Collective"), tuple(12L, "WorkDispo"));
        assertThat(dto.alsoOn().get(0).url()).isEqualTo("https://www.example.com/8");
        assertThat(dto.saved()).isTrue();
        assertThat(dto.seen()).isFalse();
    }

    @Test
    void spreadsTheRatesOfTheIntermediariesPublishingAMission() {
        JobOffer acme = publisherOffer("Acme", 550, 600);
        JobOffer acmeAgain = publisherOffer("ACME", null, 650);
        JobOffer globex = publisherOffer("Globex", 520, 560);
        JobOffer initech = publisherOffer("Initech", null, null);
        JobOffer anonymous = publisherOffer(null, 900, null);

        assertThat(OfferSearchService.spread(acme, List.of(acmeAgain, globex, initech, anonymous)))
            .isEqualTo(new OfferPublisherSpreadDto(3, 560, 650));
        assertThat(OfferSearchService.spread(acme, List.of(acmeAgain))).isNull();
        assertThat(OfferSearchService.spread(publisherOffer("Acme", null, null), List.of(initech)))
            .isEqualTo(new OfferPublisherSpreadDto(2, null, null));
    }

    private static JobOffer publisherOffer(String company, Integer rateMin, Integer rateMax) {
        JobOffer offer = new JobOffer();
        offer.setCompany(company);
        offer.setRateMin(rateMin);
        offer.setRateMax(rateMax);
        return offer;
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchesWithoutAWorkspaceOrAKnownCity() {
        when(offers.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        OfferPageDto page = service.search(null, new OfferSearchRequest());

        assertThat(page.content()).isEmpty();
        assertThat(page.distanceAvailable()).isFalse();
        verify(states, never()).findStates(any(), anyList());
        verify(offers, never()).findSkillsOf(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsAnotherPageOfTheSameFiltersWithoutCountingThemAgain() {
        PageRequest second = PageRequest.of(1, 20);
        when(offers.findPage(any(Specification.class), eq(second), eq(41L)))
            .thenReturn(new PageImpl<>(List.of(offer(7L)), second, 41));
        OfferSearchRequest request = new OfferSearchRequest();
        request.setPage(1);
        request.setTotal(41L);

        OfferPageDto page = service.search(null, request);

        assertThat(page.totalElements()).isEqualTo(41);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content()).hasSize(1);
        verify(offers, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void needsTheProfilesCityToFilterByDistance() {
        when(freelances.findProfileCity(3L)).thenReturn(Optional.of(" "));
        Freelance freelance = new Freelance();
        freelance.setCity(null);
        when(freelances.findById(3L)).thenReturn(Optional.of(freelance));
        OfferSearchRequest request = new OfferSearchRequest();
        request.setRadiusKm(30);

        assertThatThrownBy(() -> service.search(3L, request)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("city");
    }

    @Test
    void measuresFromTheWorkspacesCityWhenTheProfileHasNone() {
        Freelance freelance = new Freelance();
        freelance.setCity("Lyon");
        when(freelances.findProfileCity(3L)).thenReturn(Optional.empty());
        when(freelances.findById(3L)).thenReturn(Optional.of(freelance));
        when(geoLocator.locate("Lyon", null)).thenReturn(Optional.of(new GeoLocator.Place("Lyon", "69", "84", 45.76, 4.83)));

        assertThat(service.origin(3L)).isPresent();
    }

    @Test
    void getsOneOfferWithItsDescription() {
        JobOffer offer = offer(7L);
        offer.setExpiresAt(NOW);
        when(offers.findById(7L)).thenReturn(Optional.of(offer));
        when(offers.findSkillsOf(List.of(7L))).thenReturn(List.of());

        OfferDto dto = service.get(null, 7L);

        assertThat(dto.description()).isEqualTo("Mission longue\nà La Défense.");
        assertThat(dto.skills()).isEmpty();
        assertThat(dto.expired()).isTrue();
        assertThat(dto.distanceKm()).isNull();
        assertThat(dto.commute()).isNull();
        assertThatThrownBy(() -> service.get(null, 8L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsEverySiteWithItsHealth() {
        OfferSiteStatus failing = new OfferSiteStatus(OfferSite.YEETS, NOW);
        failing.setConsecutiveFailures(2);
        failing.setLastSuccessAt(NOW.minusSeconds(3600));
        when(statuses.findAll()).thenReturn(List.of(failing));
        when(offers.countOpenBySite(NOW)).thenReturn(List.<Object[]>of(new Object[] {OfferSite.YEETS, 12L}));

        when(exclusions.findSitesByFreelanceId(3L)).thenReturn(List.of(OfferSite.YEETS));

        List<OfferSiteDto> sites = service.sites(3L);

        assertThat(sites).hasSize(OfferSite.values().length);
        OfferSiteDto yeets = sites.stream().filter(site -> site.site() == OfferSite.YEETS).findFirst().orElseThrow();
        assertThat(yeets).isEqualTo(new OfferSiteDto(OfferSite.YEETS, "Yeets", "https://www.yeets.fr", 12, NOW.minusSeconds(3600), true, true));
        assertThat(sites.get(0).openOffers()).isZero();
        assertThat(sites.get(0).failing()).isFalse();
        assertThat(sites.get(0).excluded()).isFalse();
        assertThat(service.sites(null)).noneMatch(OfferSiteDto::excluded);
        verify(offers, times(1)).countOpenBySite(NOW);
    }

    @Test
    void suggestsSkillsByPrefixFromCountsReadOncePerMinute() {
        when(offers.countOpenSkills(NOW)).thenReturn(List.of(new Object[] {"Java", 42L}, new Object[] {"Kafka", 60L},
            new Object[] {"jakarta EE", 42L}, new Object[] {"JavaScript", 50L}));

        assertThat(service.skills(" Ja ")).containsExactly(new OfferFacetDto("JavaScript", 50), new OfferFacetDto("jakarta EE", 42),
            new OfferFacetDto("Java", 42));
        assertThat(service.skills(null)).extracting(OfferFacetDto::value).containsExactly("Kafka", "JavaScript", "jakarta EE", "Java");
        assertThat(service.skills("ja%")).isEmpty();
        verify(offers, times(1)).countOpenSkills(NOW);
    }

    @Test
    void listsRegionsWithTheirDepartments() {
        List<OfferRegionDto> regions = service.regions();

        assertThat(regions).hasSize(18);
        OfferRegionDto idf = regions.stream().filter(region -> region.regionCode().equals("11")).findFirst().orElseThrow();
        assertThat(idf.departments()).extracting(OfferRegionDto.Department::departmentCode).contains("75", "92");
    }

    @Test
    void cutsExcerptsAtAWord() {
        String description = "Une mission " + "longue ".repeat(60);

        String excerpt = OfferSearchService.excerpt(description);

        assertThat(excerpt).endsWith("longue…").hasSizeLessThanOrEqualTo(OfferSearchService.EXCERPT_LENGTH + 1);
        assertThat(OfferSearchService.excerpt("x".repeat(400))).hasSize(OfferSearchService.EXCERPT_LENGTH + 1);
        assertThat(OfferSearchService.excerpt(" ")).isNull();
        assertThat(OfferSearchService.excerpt(null)).isNull();
        assertThat(OfferSearchService.distanceKm(48.8589, 2.347, 45.764, 4.8357)).isBetween(390.0, 395.0);
    }

    private static JobOffer copy(Long id, OfferSite site, Instant publishedAt, Instant expiresAt) {
        JobOffer copy = offer(id);
        copy.setSite(site);
        copy.setUrl("https://www.example.com/" + id);
        copy.setPublishedAt(publishedAt);
        copy.setExpiresAt(expiresAt);
        copy.setCopyGroup(7L);
        return copy;
    }

    private static JobOffer offer(Long id) {
        JobOffer offer = new JobOffer();
        offer.setId(id);
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId("ext-" + id);
        offer.setUrl("https://www.free-work.com/" + id);
        offer.setTitle("Développeur Java");
        offer.setDescription("Mission longue\nà La Défense.");
        offer.setDepartmentCode("92");
        offer.setRegionCode("11");
        offer.setLatitude(48.8976);
        offer.setLongitude(2.2574);
        offer.setPublishedAt(NOW.minusSeconds(60));
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        return offer;
    }
}
