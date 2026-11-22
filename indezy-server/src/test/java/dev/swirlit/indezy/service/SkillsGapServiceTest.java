package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsGapServiceTest {

    @Test
    void showsNothingBelowTenMatchingOffersAndKeepsTheAskedSkillsOnly() {
        Map<Long, Set<String>> offers = new HashMap<>();
        for (long id = 0; id < 9; id++) {
            offers.put(id, Set.of("java", "kafka"));
        }
        offers.put(100L, Set.of("python"));
        assertThat(SkillsGapService.measure(Set.of("java"), offers, Map.of()).matchingOffers()).isEqualTo(9);
        assertThat(SkillsGapService.measure(Set.of("java"), offers, Map.of()).gaps()).isEmpty();

        offers.put(9L, Set.of("java", "rust"));
        offers.put(10L, Set.of("java", "rust"));
        SkillsGapService.SkillsGap gap = SkillsGapService.measure(Set.of("java"), offers, Map.of("kafka", "Kafka"));
        assertThat(gap.matchingOffers()).isEqualTo(11);
        // Rust: 2 offers, under the 3 needed; Kafka named as most written.
        assertThat(gap.gaps()).containsExactly(new SkillsGapService.Gap("Kafka", 9, 82));
    }
}
