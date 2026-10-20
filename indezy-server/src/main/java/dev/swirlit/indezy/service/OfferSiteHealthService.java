package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferSiteExclusionDto;
import dev.swirlit.indezy.dto.OfferSiteHealthDto;
import dev.swirlit.indezy.dto.OfferSiteHealthDto.LeftOut;
import dev.swirlit.indezy.dto.OfferSiteHealthDto.Site;
import dev.swirlit.indezy.dto.OfferSiteHealthDto.Status;
import dev.swirlit.indezy.model.OfferSiteExclusion;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferSiteExclusionRepository;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import dev.swirlit.indezy.scraping.OfferProperties;
import dev.swirlit.indezy.scraping.OfferSiteRuns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The site health page: how each job board's collection goes and which ones the workspace keeps out of its feed (reading
 * a site on request is {@code OfferSiteRefresher}'s).
 */
@Service
public class OfferSiteHealthService {

    private final OfferSiteStatusRepository statuses;
    private final JobOfferRepository offers;
    private final OfferSiteExclusionRepository exclusions;
    private final FreelanceRepository freelances;
    private final OfferProperties properties;
    private final Clock clock;

    @Autowired
    public OfferSiteHealthService(OfferSiteStatusRepository statuses, JobOfferRepository offers, OfferSiteExclusionRepository exclusions,
                                  FreelanceRepository freelances, OfferProperties properties) {
        this(statuses, offers, exclusions, freelances, properties, Clock.systemUTC());
    }

    OfferSiteHealthService(OfferSiteStatusRepository statuses, JobOfferRepository offers, OfferSiteExclusionRepository exclusions,
                           FreelanceRepository freelances, OfferProperties properties, Clock clock) {
        this.statuses = statuses;
        this.offers = offers;
        this.exclusions = exclusions;
        this.freelances = freelances;
        this.properties = properties;
        this.clock = clock;
    }

    /** Every site with its collection's health for {@code freelanceId}'s workspace (or none), and the sites left out. */
    @Transactional(readOnly = true)
    public OfferSiteHealthDto health(Long freelanceId) {
        Instant now = clock.instant();
        Map<OfferSite, OfferSiteStatus> byStatus = statuses.findAll().stream()
            .collect(Collectors.toMap(OfferSiteStatus::getSite, Function.identity()));
        Map<OfferSite, Long> open = new HashMap<>();
        for (Object[] row : offers.countOpenBySite(now)) {
            open.put((OfferSite) row[0], (Long) row[1]);
        }
        Map<OfferSite, Object[]> collected = new HashMap<>();
        for (Object[] row : offers.collectionBySite(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(7)))) {
            collected.put((OfferSite) row[0], row);
        }
        Set<OfferSite> excluded = excludedSites(freelanceId);
        List<Site> sites = Arrays.stream(OfferSite.values())
            .map(site -> site(site, byStatus.get(site), open.getOrDefault(site, 0L), collected.get(site), excluded.contains(site), now))
            .sorted(Comparator.comparing((Site site) -> site.status() != Status.FAILING).thenComparing(Site::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<LeftOut> leftOut = Arrays.stream(OfferLeftOutSite.values())
            .map(site -> new LeftOut(site, site.getDisplayName(), site.getHomeUrl()))
            .toList();
        return new OfferSiteHealthDto(sites, leftOut, properties.collectionEnabled());
    }

    /** The sites {@code freelanceId}'s workspace keeps out of its feed; none without a workspace. */
    @Transactional(readOnly = true)
    public Set<OfferSite> excludedSites(Long freelanceId) {
        Set<OfferSite> excluded = EnumSet.noneOf(OfferSite.class);
        if (freelanceId != null) {
            excluded.addAll(exclusions.findSitesByFreelanceId(freelanceId));
        }
        return excluded;
    }

    /** Keeps {@code site} out of the workspace's feed, saved searches and market snapshot, or brings it back. */
    @Transactional
    public OfferSiteExclusionDto setExcluded(Long freelanceId, OfferSite site, boolean excluded) {
        if (!excluded) {
            exclusions.deleteByFreelanceIdAndSite(freelanceId, site);
        } else if (!exclusions.existsByFreelanceIdAndSite(freelanceId, site)) {
            OfferSiteExclusion exclusion = new OfferSiteExclusion();
            exclusion.setFreelance(freelances.getReferenceById(freelanceId));
            exclusion.setSite(site);
            exclusions.save(exclusion);
        }
        return new OfferSiteExclusionDto(site, excluded);
    }

    /** A site never read has no status yet: it reads as a blank one. */
    private Site site(OfferSite site, OfferSiteStatus found, long open, Object[] collected, boolean excluded, Instant now) {
        OfferSiteStatus status = found != null ? found : new OfferSiteStatus(site, null);
        Instant availableAt = OfferSiteRuns.refreshAvailableAt(status);
        long day = collected == null ? 0 : ((Number) collected[1]).longValue();
        long week = collected == null ? 0 : ((Number) collected[2]).longValue();
        return new Site(site, site.getDisplayName(), site.getHomeUrl(), status(status, now), status.getLastRunAt(),
            status.getLastSuccessAt(), status.getNextRunAt(), status.getLastError(), status.getConsecutiveFailures(),
            status.getLastFound(), status.getLastNew(), day, week, open, collected == null ? null : (Instant) collected[3], excluded,
            availableAt != null && availableAt.isAfter(now) ? availableAt : null);
    }

    Status status(OfferSiteStatus status, Instant now) {
        if (status.getRunningSince() != null && status.getRunningSince().isAfter(now.minus(properties.staleRunAfter()))) {
            return Status.RUNNING;
        }
        if (status.getConsecutiveFailures() > 0) {
            return Status.FAILING;
        }
        return status.getLastSuccessAt() == null ? Status.WAITING : Status.OK;
    }
}
