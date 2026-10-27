package dev.swirlit.indezy.service;

import dev.swirlit.indezy.admin.AdminProperties;
import dev.swirlit.indezy.model.FeatureFlagState;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.repository.FeatureFlagStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureFlagServiceTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-27T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void keepsEveryFeatureOnUntilTurnedOffAndReadsTheStatesEveryThirtySeconds() {
        FeatureFlagStateRepository states = mock(FeatureFlagStateRepository.class);
        List<FeatureFlagState> rows = new ArrayList<>();
        when(states.findAll()).thenReturn(rows);
        when(states.findByFlag(any())).thenReturn(Optional.empty());
        when(states.save(any())).thenAnswer(invocation -> {
            rows.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        FeatureFlagService flags = new FeatureFlagService(states, clock);

        assertThat(flags.all()).hasSize(FeatureFlag.values().length).allSatisfy((flag, enabled) -> assertThat(enabled).isTrue());
        flags.set(FeatureFlag.DATA_IMPORT, false);
        assertThat(flags.isEnabled(FeatureFlag.DATA_IMPORT)).isFalse();
        verify(states, times(2)).findAll();

        // Another replica's change shows once the states are older than 30 seconds.
        rows.clear();
        flags.isEnabled(FeatureFlag.OFFER_FEED);
        verify(states, times(2)).findAll();
        now.set(now.get().plusSeconds(30));
        assertThat(flags.isEnabled(FeatureFlag.DATA_IMPORT)).isTrue();
        verify(states, times(3)).findAll();
    }

    @Test
    void matchesTheApiPathsOfEachFeature() {
        assertThat(FeatureFlag.OFFER_FEED.covers("/offers")).isTrue();
        assertThat(FeatureFlag.OFFER_FEED.covers("/offers/searches/3")).isTrue();
        assertThat(FeatureFlag.OFFER_FEED.covers("/offers-archive")).isFalse();
        assertThat(FeatureFlag.PUBLIC_PROFILES.covers("/public/profiles/alice")).isTrue();
        assertThat(Arrays.stream(FeatureFlag.values()).anyMatch(flag -> flag.covers("/public/saved-search-emails/unsubscribe")))
            .isFalse();
    }

    @Test
    void namesAdministratorsByEmailCaseAside() {
        AdminProperties admins = new AdminProperties(List.of(" Chief@Example.com ", "", "other@example.com"));
        assertThat(admins.isAdmin("chief@example.COM")).isTrue();
        assertThat(admins.isAdmin("someone@example.com")).isFalse();
        assertThat(admins.isAdmin(null)).isFalse();
        assertThat(new AdminProperties(null).emails()).isEmpty();
    }
}
