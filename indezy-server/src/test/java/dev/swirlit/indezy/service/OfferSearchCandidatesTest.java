package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfferSearchCandidatesTest {

    @Test
    void suggestsTheJobTitleTheMainSkillsTheirPairAndEachWayOfWorking() {
        OfferSearchCandidates.Inputs inputs = new OfferSearchCandidates.Inputs(List.of("Java", "Spring Boot", "Kafka"), " Tech lead Java ", 600,
            "Lyon", "84", LocalDate.of(2026, 11, 16), List.of("Angular"));

        List<OfferSearchRequest> candidates = OfferSearchCandidates.of(inputs, List.of("Java", "Kafka"));

        assertThat(candidates).extracting(OfferSearchRequest::getQ).containsExactly("Tech lead Java", null, null, null, null, null, null,
            null, null);
        assertThat(candidates).extracting(OfferSearchRequest::getSkills).containsExactly(List.of(), List.of("Java"), List.of("Spring Boot"),
            List.of("Java", "Kafka"), List.of("Java"), List.of("Java"), List.of("Java"), List.of("Java"), List.of("Angular"));
        assertThat(candidates).extracting(OfferSearchRequest::getRadiusKm).containsExactly(30, 30, 30, null, null, null, 80, 30, 30);
        assertThat(candidates).extracting(OfferSearchRequest::getRateMin).containsOnly(600);
        assertThat(candidates.get(4).getWorkModes()).containsExactly(WorkMode.REMOTE);
        assertThat(candidates.get(5).getRegions()).containsExactly("84");
        assertThat(candidates.get(6).getWorkModes()).containsExactly(WorkMode.HYBRID);
        assertThat(candidates.get(6).getMinRemoteDays()).isEqualTo(2);
        assertThat(candidates.get(7).getStartBefore()).isEqualTo(LocalDate.of(2026, 12, 16));
    }

    @Test
    void suggestsWhatTheProfileAllowsWithoutCityTitleOrRate() {
        OfferSearchCandidates.Inputs skillsOnly = new OfferSearchCandidates.Inputs(List.of("Go"), "  ", null, null, null, null, List.of());

        List<OfferSearchRequest> candidates = OfferSearchCandidates.of(skillsOnly, List.of());

        // Go alone, Go remote, Go hybrid: no title, pair, region, availability or pipeline skill.
        assertThat(candidates).extracting(OfferSearchRequest::getSkills).containsExactly(List.of("Go"), List.of("Go"), List.of("Go"));
        assertThat(candidates).extracting(OfferSearchRequest::getRadiusKm).containsOnlyNulls();
        assertThat(candidates).extracting(OfferSearchRequest::getRateMin).containsOnlyNulls();
        assertThat(skillsOnly.position()).isNull();
        assertThat(skillsOnly.near()).isFalse();
        assertThat(OfferSearchCandidates.of(new OfferSearchCandidates.Inputs(List.of(), null, 500, null, null, null, List.of("SAP")), List.of()))
            .extracting(OfferSearchRequest::getSkills).containsExactly(List.of("SAP"));
    }

    @Test
    void pairsTheFirstFourSkills() {
        assertThat(OfferSearchCandidates.pairs(List.of("A", "B", "C", "D", "E"))).containsExactly(List.of("A", "B"), List.of("A", "C"),
            List.of("A", "D"), List.of("B", "C"), List.of("B", "D"), List.of("C", "D"));
        assertThat(OfferSearchCandidates.pairs(List.of("A"))).isEmpty();
    }

    @Test
    void takesThePipelinesMostNamedSkillsTheProfileLacks() {
        List<String> stacks = Arrays.asList("Java, Spring Boot / Angular", null, "angular; Docker", "Docker|Kubernetes\nAngular",
            "Spring boot, " + "x".repeat(61));

        assertThat(OfferSearchCandidates.pipelineSkills(stacks, List.of("Java", "Spring Boot"))).containsExactly("Angular", "Docker");
        assertThat(OfferSearchCandidates.pipelineSkills(List.of(" , "), List.of())).isEmpty();
    }

    @Test
    void readsTheMedianOfThePipelinesRates() {
        assertThat(OfferSearchCandidates.medianRate(Arrays.asList(600, null, 500, 0, 700))).isEqualTo(600);
        assertThat(OfferSearchCandidates.medianRate(List.of(500, 650))).isEqualTo(575);
        assertThat(OfferSearchCandidates.medianRate(List.of())).isNull();
    }
}
