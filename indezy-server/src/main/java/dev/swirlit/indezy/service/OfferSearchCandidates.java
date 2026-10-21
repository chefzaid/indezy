package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.util.TextFolding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The searches worth suggesting to a workspace, before the feed tells which find offers (see
 * docs/features.md#suggested-searches): its job title, its main skills alone, in the pair offers ask for most and with
 * each way of working (near its city, in its region, full remote, hybrid with two remote days or more, starting soon
 * after it is available), and the skills its recent opportunities name that its profile does not, all at its target
 * daily rate or more.
 */
final class OfferSearchCandidates {

    /** The radius of "near the city", as the profile match counts it. */
    static final int NEAR_KM = 30;
    /** How far a hybrid mission is worth it with two remote days a week. */
    static final int HYBRID_KM = 80;
    static final int HYBRID_REMOTE_DAYS = 2;
    /** Missions starting by this many days after the freelance is available. */
    static final int START_WITHIN_DAYS = 30;
    /** Profile skills suggested alone, and looked at for a pair. */
    static final int SKILLS_ALONE = 2;
    static final int SKILLS_FOR_PAIRS = 4;
    /** Skills of the pipeline suggested at most. */
    static final int PIPELINE_SKILLS = 2;
    /** Longest skill a search names (see {@link OfferSearchRequest#getSkills()}). */
    private static final int MAX_SKILL_LENGTH = 60;

    /**
     * What suggestions are drawn from: the profile's skills, job title, target daily rate (else the pipeline's median),
     * city (its name and region) and availability, and the skills of the pipeline.
     */
    record Inputs(List<String> skills, String position, Integer rate, String city, String regionCode, LocalDate availableFrom,
                  List<String> pipelineSkills) {

        Inputs {
            skills = List.copyOf(skills);
            pipelineSkills = List.copyOf(pipelineSkills);
            position = position == null || position.isBlank() ? null : position.strip();
        }

        boolean near() {
            return city != null;
        }
    }

    private OfferSearchCandidates() {
    }

    /** The searches to try, most telling first, each at the target rate or more; {@code pair}, when two skills, too. */
    static List<OfferSearchRequest> of(Inputs inputs, List<String> pair) {
        List<OfferSearchRequest> candidates = new ArrayList<>();
        if (inputs.position() != null) {
            candidates.add(search(inputs, search -> {
                search.setQ(inputs.position());
                near(inputs, search);
            }));
        }
        for (String skill : inputs.skills().subList(0, Math.min(SKILLS_ALONE, inputs.skills().size()))) {
            candidates.add(search(inputs, search -> {
                search.getSkills().add(skill);
                near(inputs, search);
            }));
        }
        if (pair.size() == 2) {
            candidates.add(search(inputs, search -> search.getSkills().addAll(pair)));
        }
        if (!inputs.skills().isEmpty()) {
            String main = inputs.skills().get(0);
            candidates.add(search(inputs, search -> {
                search.getSkills().add(main);
                search.getWorkModes().add(WorkMode.REMOTE);
            }));
            if (inputs.regionCode() != null) {
                candidates.add(search(inputs, search -> {
                    search.getSkills().add(main);
                    search.getRegions().add(inputs.regionCode());
                }));
            }
            candidates.add(search(inputs, search -> {
                search.getSkills().add(main);
                search.getWorkModes().add(WorkMode.HYBRID);
                search.setMinRemoteDays(HYBRID_REMOTE_DAYS);
                if (inputs.near()) {
                    search.setRadiusKm(HYBRID_KM);
                }
            }));
            if (inputs.availableFrom() != null) {
                candidates.add(search(inputs, search -> {
                    search.getSkills().add(main);
                    search.setStartBefore(inputs.availableFrom().plusDays(START_WITHIN_DAYS));
                    near(inputs, search);
                }));
            }
        }
        for (String skill : inputs.pipelineSkills()) {
            candidates.add(search(inputs, search -> {
                search.getSkills().add(skill);
                near(inputs, search);
            }));
        }
        return candidates;
    }

    /** The pairs of the profile's first skills, to count which offers ask for most. */
    static List<List<String>> pairs(List<String> skills) {
        List<String> first = skills.subList(0, Math.min(SKILLS_FOR_PAIRS, skills.size()));
        List<List<String>> pairs = new ArrayList<>();
        for (int i = 0; i < first.size(); i++) {
            for (int j = i + 1; j < first.size(); j++) {
                pairs.add(List.of(first.get(i), first.get(j)));
            }
        }
        return pairs;
    }

    /**
     * The skills the pipeline's tech stacks ("Java, Spring Boot / Kafka") name most that the profile does not, as first
     * written, at most {@value #PIPELINE_SKILLS}.
     */
    static List<String> pipelineSkills(Collection<String> stacks, List<String> profileSkills) {
        Set<String> known = new LinkedHashSet<>();
        profileSkills.forEach(skill -> known.add(TextFolding.words(skill)));
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> spelling = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (String stack : stacks) {
            if (stack == null) {
                continue;
            }
            for (String part : stack.split("[,;/|\\n]")) {
                String skill = part.strip();
                String folded = TextFolding.words(skill);
                if (folded == null || folded.isBlank() || skill.length() > MAX_SKILL_LENGTH || known.contains(folded)) {
                    continue;
                }
                if (spelling.putIfAbsent(folded, skill) == null) {
                    order.add(folded);
                }
                counts.merge(folded, 1, Integer::sum);
            }
        }
        // Most named first, the first named first among equals.
        return order.stream()
            .sorted((a, b) -> Integer.compare(counts.get(b), counts.get(a)))
            .limit(PIPELINE_SKILLS)
            .map(spelling::get)
            .toList();
    }

    /** The median of the pipeline's daily rates, or {@code null} without any. */
    static Integer medianRate(Collection<Integer> rates) {
        List<Integer> sorted = rates.stream().filter(rate -> rate != null && rate > 0).sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static OfferSearchRequest search(Inputs inputs, Consumer<OfferSearchRequest> setup) {
        OfferSearchRequest search = new OfferSearchRequest();
        search.setRateMin(inputs.rate());
        setup.accept(search);
        return search;
    }

    private static void near(Inputs inputs, OfferSearchRequest search) {
        if (inputs.near()) {
            search.setRadiusKm(NEAR_KM);
        }
    }
}
