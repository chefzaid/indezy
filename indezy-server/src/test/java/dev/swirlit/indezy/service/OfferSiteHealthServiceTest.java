package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferSiteExclusionDto;
import dev.swirlit.indezy.dto.OfferSiteHealthDto;
import dev.swirlit.indezy.dto.OfferSiteHealthDto.Site;
import dev.swirlit.indezy.dto.OfferSiteHealthDto.Status;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.OfferSiteExclusion;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferSiteExclusionRepository;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import dev.swirlit.indezy.scraping.OfferProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSiteHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final OfferSiteStatusRepository statuses = mock(OfferSiteStatusRepository.class);
    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final OfferSiteExclusionRepository exclusions = mock(OfferSiteExclusionRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final OfferSiteHealthService service = new OfferSiteHealthService(statuses, offers, exclusions, freelances,
        properties(true), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void describesEachSitesCollectionWithTheFailingOnesFirst() {
        OfferSiteStatus freeWork = status(OfferSite.FREE_WORK, NOW.minus(Duration.ofMinutes(10)));
        freeWork.setLastSuccessAt(NOW.minus(Duration.ofMinutes(10)));
        freeWork.setLastFound(120);
        freeWork.setLastNew(4);
        OfferSiteStatus yeets = status(OfferSite.YEETS, NOW.minus(Duration.ofHours(2)));
        yeets.setConsecutiveFailures(3);
        yeets.setLastError("SiteResponseException: 503");
        OfferSiteStatus lehibou = status(OfferSite.LEHIBOU, NOW.minus(Duration.ofHours(1)));
        lehibou.setRunningSince(NOW.minus(Duration.ofMinutes(2)));
        OfferSiteStatus stuck = status(OfferSite.COLLECTIVE, NOW.minus(Duration.ofHours(3)));
        stuck.setLastSuccessAt(NOW.minus(Duration.ofHours(3)));
        stuck.setRunningSince(NOW.minus(Duration.ofHours(2)));
        when(statuses.findAll()).thenReturn(List.of(freeWork, yeets, lehibou, stuck));
        when(offers.countOpenBySite(NOW)).thenReturn(List.<Object[]>of(new Object[] {OfferSite.FREE_WORK, 812L}));
        when(offers.collectionBySite(NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(7))))
            .thenReturn(List.<Object[]>of(new Object[] {OfferSite.FREE_WORK, 40L, 210L, NOW.minus(Duration.ofHours(1))}));
        when(exclusions.findSitesByFreelanceId(3L)).thenReturn(List.of(OfferSite.YEETS));

        OfferSiteHealthDto health = service.health(3L);

        assertThat(health.refreshEnabled()).isTrue();
        assertThat(health.sites()).hasSize(OfferSite.values().length);
        assertThat(health.sites().get(0)).isEqualTo(new Site(OfferSite.YEETS, "Yeets", "https://www.yeets.fr", Status.FAILING,
            NOW.minus(Duration.ofHours(2)), null, NOW.plus(Duration.ofHours(1)), "SiteResponseException: 503", 3, null, null, 0, 0, 0,
            null, true, null));
        assertThat(health.sites().get(1).name()).isEqualTo("Beyond Skills IT");
        assertThat(site(health, OfferSite.FREE_WORK)).isEqualTo(new Site(OfferSite.FREE_WORK, "Free-Work", "https://www.free-work.com",
            Status.OK, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofMinutes(10)), NOW.plus(Duration.ofHours(1)), null, 0, 120, 4,
            40, 210, 812, NOW.minus(Duration.ofHours(1)), false, NOW.plus(Duration.ofMinutes(5))));
        assertThat(site(health, OfferSite.LEHIBOU).status()).isEqualTo(Status.RUNNING);
        // A run that never finished an hour on no longer holds its site.
        assertThat(site(health, OfferSite.COLLECTIVE).status()).isEqualTo(Status.OK);
        assertThat(site(health, OfferSite.KOINO).status()).isEqualTo(Status.WAITING);
        assertThat(site(health, OfferSite.KOINO).refreshAvailableAt()).isNull();
        assertThat(health.leftOut()).hasSize(OfferLeftOutSite.values().length).first()
            .isEqualTo(new OfferSiteHealthDto.LeftOut(OfferLeftOutSite.LINKEDIN, "LinkedIn", "https://www.linkedin.com"));
    }

    @Test
    void tellsWhenReadingOnRequestIsOff() {
        OfferSiteHealthService off = new OfferSiteHealthService(statuses, offers, exclusions, freelances, properties(false),
            Clock.fixed(NOW, ZoneOffset.UTC));

        OfferSiteHealthDto health = off.health(null);

        assertThat(health.refreshEnabled()).isFalse();
        assertThat(health.sites()).noneMatch(Site::excluded);
        verify(exclusions, never()).findSitesByFreelanceId(any());
    }

    @Test
    void excludesASiteOnceAndBringsItBack() {
        Freelance freelance = new Freelance();
        when(freelances.getReferenceById(3L)).thenReturn(freelance);
        when(exclusions.existsByFreelanceIdAndSite(3L, OfferSite.YEETS)).thenReturn(false, true);

        assertThat(service.setExcluded(3L, OfferSite.YEETS, true)).isEqualTo(new OfferSiteExclusionDto(OfferSite.YEETS, true));
        service.setExcluded(3L, OfferSite.YEETS, true);
        assertThat(service.setExcluded(3L, OfferSite.YEETS, false)).isEqualTo(new OfferSiteExclusionDto(OfferSite.YEETS, false));

        ArgumentCaptor<OfferSiteExclusion> saved = ArgumentCaptor.forClass(OfferSiteExclusion.class);
        verify(exclusions).save(saved.capture());
        assertThat(saved.getValue().getFreelance()).isSameAs(freelance);
        assertThat(saved.getValue().getSite()).isEqualTo(OfferSite.YEETS);
        verify(exclusions).deleteByFreelanceIdAndSite(3L, OfferSite.YEETS);
    }

    private static Site site(OfferSiteHealthDto health, OfferSite site) {
        return health.sites().stream().filter(found -> found.site() == site).findFirst().orElseThrow();
    }

    private static OfferSiteStatus status(OfferSite site, Instant lastRunAt) {
        OfferSiteStatus status = new OfferSiteStatus(site, NOW.plus(Duration.ofHours(1)));
        status.setLastRunAt(lastRunAt);
        return status;
    }

    static OfferProperties properties(boolean collectionEnabled) {
        return new OfferProperties(collectionEnabled, Duration.ofMinutes(5), Duration.ofSeconds(2), 10, Duration.ofHours(1), 90,
            "IndezyBot/test", false, "http://127.0.0.1");
    }
}
