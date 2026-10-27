package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.FeatureFlagState;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.repository.FeatureFlagStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Which features are on. The states are read again at most every {@code REFRESH} seconds, so every replica follows a
 * change within that time; the one that made it follows at once.
 */
@Service
public class FeatureFlagService {

    static final Duration REFRESH = Duration.ofSeconds(30);

    private final FeatureFlagStateRepository states;
    private final Clock clock;
    private volatile Map<FeatureFlag, Boolean> snapshot = Map.of();
    private volatile Instant readAt = Instant.MIN;

    @Autowired
    public FeatureFlagService(FeatureFlagStateRepository states) {
        this(states, Clock.systemUTC());
    }

    FeatureFlagService(FeatureFlagStateRepository states, Clock clock) {
        this.states = states;
        this.clock = clock;
    }

    public boolean isEnabled(FeatureFlag flag) {
        return all().get(flag);
    }

    /** Every feature and whether it is on. */
    public Map<FeatureFlag, Boolean> all() {
        if (Duration.between(readAt, clock.instant()).compareTo(REFRESH) >= 0) {
            read();
        }
        return snapshot;
    }

    @Transactional
    public Map<FeatureFlag, Boolean> set(FeatureFlag flag, boolean enabled) {
        FeatureFlagState state = states.findByFlag(flag).orElseGet(() -> {
            FeatureFlagState created = new FeatureFlagState();
            created.setFlag(flag);
            return created;
        });
        state.setEnabled(enabled);
        states.save(state);
        read();
        return snapshot;
    }

    private synchronized void read() {
        Map<FeatureFlag, Boolean> read = new EnumMap<>(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
            read.put(flag, true);
        }
        states.findAll().forEach(state -> read.put(state.getFlag(), state.isEnabled()));
        snapshot = Map.copyOf(read);
        readAt = clock.instant();
    }
}
