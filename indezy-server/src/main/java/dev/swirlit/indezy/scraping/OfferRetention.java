package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferSkipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Removes offers published more than {@code indezy.offers.retention-days} ago or past their expiry,
 * with what workspaces did with them, unless a workspace saved or imported them; and forgets skipped offers
 * after {@link #SKIP_MEMORY}.
 */
@Slf4j
@Service
public class OfferRetention {

    private static final int CHUNK = 500;
    /** After this long, a skipped offer is read again: its site may have opened it to freelancers. */
    static final Duration SKIP_MEMORY = Duration.ofDays(30);

    private final JobOfferRepository offers;
    private final JobOfferStateRepository states;
    private final OfferSkipRepository skips;
    private final OfferProperties properties;
    private final Clock clock;

    @Autowired
    public OfferRetention(JobOfferRepository offers, JobOfferStateRepository states, OfferSkipRepository skips,
                          OfferProperties properties) {
        this(offers, states, skips, properties, Clock.systemUTC());
    }

    OfferRetention(JobOfferRepository offers, JobOfferStateRepository states, OfferSkipRepository skips,
                   OfferProperties properties, Clock clock) {
        this.offers = offers;
        this.states = states;
        this.skips = skips;
        this.properties = properties;
        this.clock = clock;
    }

    /** How many offers were removed. */
    @Transactional
    public int purge() {
        Instant now = clock.instant();
        List<Long> ids = offers.findDisposableIds(now.minus(Duration.ofDays(properties.retentionDays())), now);
        for (int start = 0; start < ids.size(); start += CHUNK) {
            List<Long> chunk = ids.subList(start, Math.min(ids.size(), start + CHUNK));
            states.deleteByOfferIdIn(chunk);
            offers.deleteSkillsOf(chunk);
            offers.deleteByIdIn(chunk);
        }
        skips.deleteSkippedBefore(now.minus(SKIP_MEMORY));
        if (!ids.isEmpty()) {
            log.info("Removed {} old or expired job offers", ids.size());
        }
        return ids.size();
    }
}
