package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferAppearanceRepository;
import dev.swirlit.indezy.repository.OfferChangeRepository;
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
 * with what workspaces did with them and their changes, unless a workspace saved, applied to or imported them; and forgets
 * skipped offers after {@link #SKIP_MEMORY} and the references missions were published under after {@link #APPEARANCE_MEMORY}.
 */
@Slf4j
@Service
public class OfferRetention {

    private static final int CHUNK = 500;
    /** After this long, a skipped offer is read again: its site may have opened it to freelancers. */
    static final Duration SKIP_MEMORY = Duration.ofDays(30);
    /** How long the references a mission was published under are kept, to tell how often it came back. */
    static final Duration APPEARANCE_MEMORY = Duration.ofDays(90);

    private final JobOfferRepository offers;
    private final JobOfferStateRepository states;
    private final OfferSkipRepository skips;
    private final OfferChangeRepository changes;
    private final OfferAppearanceRepository appearances;
    private final OfferProperties properties;
    private final Clock clock;

    @Autowired
    public OfferRetention(JobOfferRepository offers, JobOfferStateRepository states, OfferSkipRepository skips, OfferChangeRepository changes,
                          OfferAppearanceRepository appearances, OfferProperties properties) {
        this(offers, states, skips, changes, appearances, properties, Clock.systemUTC());
    }

    OfferRetention(JobOfferRepository offers, JobOfferStateRepository states, OfferSkipRepository skips, OfferChangeRepository changes,
                   OfferAppearanceRepository appearances, OfferProperties properties, Clock clock) {
        this.offers = offers;
        this.states = states;
        this.skips = skips;
        this.changes = changes;
        this.appearances = appearances;
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
            changes.deleteByOfferIdIn(chunk);
            offers.deleteSkillsOf(chunk);
            offers.deleteByIdIn(chunk);
        }
        skips.deleteSkippedBefore(now.minus(SKIP_MEMORY));
        appearances.forgetBefore(now.minus(APPEARANCE_MEMORY));
        if (!ids.isEmpty()) {
            log.info("Removed {} old or expired job offers", ids.size());
        }
        return ids.size();
    }
}
