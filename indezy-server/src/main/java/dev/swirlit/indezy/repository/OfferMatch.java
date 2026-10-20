package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.OfferMatchDto;
import dev.swirlit.indezy.dto.OfferMatchDto.PlaceFit;
import dev.swirlit.indezy.dto.OfferMatchDto.RateFit;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.JobOfferSpecifications.Origin;
import dev.swirlit.indezy.util.TextFolding;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * How well an offer fits the workspace's profile, out of 100: up to {@value #SKILL_POINTS} points for the profile's
 * skills it mentions (full marks from {@value #SKILLS_FOR_FULL}), {@value #RATE_POINTS} for paying the target daily
 * rate and {@value #PLACE_POINTS} for being remote or near the profile's city, over the points the profile can earn:
 * a profile without a target rate or a city is scored on the rest, one without skills is not scored. The same rule
 * runs in Java for the cards and in SQL for the sort and the "matches my profile" filter.
 */
public final class OfferMatch {

    static final int SKILL_POINTS = 60;
    static final int RATE_POINTS = 25;
    static final int PLACE_POINTS = 15;
    /** Skills an offer mentions for full skill points. */
    static final int SKILLS_FOR_FULL = 3;
    /** Score from which an offer matches the profile. */
    public static final int GOOD_MATCH = 60;
    /** Profile skills an offer is measured against at most. */
    static final int MAX_SKILLS = 20;
    /** Shorter skills ("Go", "R", "C#") are looked for in the offer's skill list only: in a text they are common words. */
    static final int MIN_TEXT_SKILL_LENGTH = 3;
    static final int NEAR_KM = 30;
    static final int REACHABLE_KM = 80;
    /** Remote days a week that make a far hybrid mission workable. */
    static final int MOSTLY_REMOTE_DAYS = 3;

    private static final Map<RateFit, Integer> RATE = Map.of(RateFit.MEETS, RATE_POINTS, RateFit.CLOSE, 15, RateFit.UNKNOWN, 10,
        RateFit.BELOW, 0);
    private static final Map<PlaceFit, Integer> PLACE = Map.of(PlaceFit.REMOTE, PLACE_POINTS, PlaceFit.NEAR, PLACE_POINTS,
        PlaceFit.REACHABLE, 10, PlaceFit.MOSTLY_REMOTE, 5, PlaceFit.UNKNOWN, 5, PlaceFit.FAR, 0);

    /**
     * What offers are measured against: the profile's skills (distinct once folded, at most {@value #MAX_SKILLS}), its
     * target daily rate and where it lives, each optional.
     */
    public record Profile(List<String> skills, Integer targetRate, Origin origin) {

        public static final Profile NONE = new Profile(List.of(), null, null);

        public Profile {
            Map<String, String> byFolded = new LinkedHashMap<>();
            for (String skill : skills == null ? List.<String>of() : skills) {
                String folded = TextFolding.words(skill);
                if (folded != null && !folded.isEmpty() && byFolded.size() < MAX_SKILLS) {
                    byFolded.putIfAbsent(folded, skill.strip());
                }
            }
            skills = List.copyOf(byFolded.values());
            targetRate = targetRate != null && targetRate > 0 ? targetRate : null;
        }

        /** Where distances are measured from, without the rest. */
        public static Profile at(Origin origin) {
            return new Profile(List.of(), null, origin);
        }

        /** Whether offers can be scored: the profile names skills. */
        public boolean canMatch() {
            return !skills.isEmpty();
        }

        /** The points the profile can earn. */
        int available() {
            return SKILL_POINTS + (targetRate == null ? 0 : RATE_POINTS) + (origin == null ? 0 : PLACE_POINTS);
        }

        /** Full skill points from this many skills mentioned. */
        int skillsForFull() {
            return Math.min(SKILLS_FOR_FULL, skills.size());
        }
    }

    private OfferMatch() {
    }

    /**
     * The offer's score with what makes it up, or {@code null} when the profile names no skills. {@code skills} is the
     * offer's skill list, read apart from it.
     */
    public static OfferMatchDto score(JobOffer offer, List<String> skills, Profile profile) {
        if (!profile.canMatch()) {
            return null;
        }
        List<String> listed = skills == null ? List.of() : skills;
        List<String> mentioned = profile.skills().stream()
            .filter(skill -> mentions(offer.getSearchText(), listed, TextFolding.words(skill)))
            .toList();
        RateFit rate = profile.targetRate() == null ? null : rateFit(offer, profile.targetRate());
        PlaceFit place = profile.origin() == null ? null : placeFit(offer, profile.origin());
        int points = Math.min(mentioned.size(), profile.skillsForFull()) * (SKILL_POINTS / profile.skillsForFull()) +
            (rate == null ? 0 : RATE.get(rate)) + (place == null ? 0 : PLACE.get(place));
        return new OfferMatchDto((int) Math.round(100.0 * points / profile.available()), mentioned, rate, place);
    }

    /** A folded skill in the offer's search text, as a whole word, or (when short) in its skill list. */
    static boolean mentions(String searchText, List<String> skills, String skill) {
        if (skill.length() < MIN_TEXT_SKILL_LENGTH) {
            return skills.stream().anyMatch(listed -> listed.toLowerCase(Locale.ROOT).equals(skill));
        }
        String text = " " + Objects.requireNonNullElse(searchText, "") + " ";
        return text.contains(" " + skill + " ") || text.contains(" " + skill + ". ");
    }

    static RateFit rateFit(JobOffer offer, int target) {
        Integer rate = bestRate(offer.getRateMin(), offer.getRateMax());
        if (rate == null) {
            return RateFit.UNKNOWN;
        }
        if (rate >= target) {
            return RateFit.MEETS;
        }
        return rate * 10 >= target * 9 ? RateFit.CLOSE : RateFit.BELOW;
    }

    /** The best daily rate an offer pays: the top of its range, or the one rate it states. */
    public static Integer bestRate(Integer rateMin, Integer rateMax) {
        return rateMax != null ? rateMax : rateMin;
    }

    static PlaceFit placeFit(JobOffer offer, Origin origin) {
        if (offer.getWorkMode() == WorkMode.REMOTE) {
            return PlaceFit.REMOTE;
        }
        if (offer.getLatitude() == null || offer.getLongitude() == null) {
            return PlaceFit.UNKNOWN;
        }
        double squared = JobOfferSpecifications.squaredDistanceKm(origin, offer.getLatitude(), offer.getLongitude());
        if (squared <= NEAR_KM * NEAR_KM) {
            return PlaceFit.NEAR;
        }
        if (squared <= REACHABLE_KM * REACHABLE_KM) {
            return PlaceFit.REACHABLE;
        }
        boolean mostlyRemote = offer.getWorkMode() == WorkMode.HYBRID && offer.getRemoteDaysPerWeek() != null &&
            offer.getRemoteDaysPerWeek() >= MOSTLY_REMOTE_DAYS;
        return mostlyRemote ? PlaceFit.MOSTLY_REMOTE : PlaceFit.FAR;
    }

    /** The offer's points as SQL, the same as {@link #score}'s before they are turned into a score. */
    static Expression<Integer> points(Profile profile, Root<JobOffer> root, CommonAbstractCriteria query, CriteriaBuilder cb) {
        Expression<Integer> mentioned = cb.literal(0);
        for (String skill : profile.skills()) {
            mentioned = cb.sum(mentioned, cb.<Integer>selectCase().when(mentions(TextFolding.words(skill), root, query, cb), 1).otherwise(0));
        }
        int full = profile.skillsForFull();
        Expression<Integer> points = cb.prod(cb.<Integer>selectCase().when(cb.ge(mentioned, full), cb.literal(full)).otherwise(mentioned),
            SKILL_POINTS / full);
        if (profile.targetRate() != null) {
            points = cb.sum(points, ratePoints(profile.targetRate(), root, cb));
        }
        if (profile.origin() != null) {
            points = cb.sum(points, placePoints(profile.origin(), root, cb));
        }
        return points;
    }

    /** Whether the offer's score reaches {@value #GOOD_MATCH} once rounded: 100 × points / available ≥ 59.5. */
    static Predicate good(Profile profile, Root<JobOffer> root, CommonAbstractCriteria query, CriteriaBuilder cb) {
        return cb.ge(cb.prod(points(profile, root, query, cb), 200), (2 * GOOD_MATCH - 1) * profile.available());
    }

    private static Predicate mentions(String skill, Root<JobOffer> root, CommonAbstractCriteria query, CriteriaBuilder cb) {
        if (skill.length() < MIN_TEXT_SKILL_LENGTH) {
            Subquery<Long> listed = query.subquery(Long.class);
            Root<JobOffer> offer = listed.from(JobOffer.class);
            Join<JobOffer, String> skills = offer.join("skills");
            listed.select(offer.get("id")).where(cb.equal(offer, root), cb.equal(cb.lower(skills), skill));
            return cb.exists(listed);
        }
        Expression<String> text = cb.concat(cb.concat(" ", cb.coalesce(root.<String>get("searchText"), "")), " ");
        String word = JobOfferSpecifications.escapeLike(skill);
        return cb.or(cb.like(text, "% " + word + " %", '\\'), cb.like(text, "% " + word + ". %", '\\'));
    }

    private static Expression<Integer> ratePoints(int target, Root<JobOffer> root, CriteriaBuilder cb) {
        Expression<Integer> rate = cb.coalesce(root.<Integer>get("rateMax"), root.<Integer>get("rateMin"));
        return cb.<Integer>selectCase()
            .when(cb.isNull(rate), RATE.get(RateFit.UNKNOWN))
            .when(cb.ge(rate, target), RATE.get(RateFit.MEETS))
            .when(cb.ge(cb.prod(rate, 10), target * 9), RATE.get(RateFit.CLOSE))
            .otherwise(RATE.get(RateFit.BELOW));
    }

    private static Expression<Integer> placePoints(Origin origin, Root<JobOffer> root, CriteriaBuilder cb) {
        Expression<Double> squared = JobOfferSpecifications.squaredDistance(origin, root, cb);
        Predicate mostlyRemote = cb.and(cb.equal(root.get("workMode"), WorkMode.HYBRID),
            cb.ge(root.<Integer>get("remoteDaysPerWeek"), MOSTLY_REMOTE_DAYS));
        return cb.<Integer>selectCase()
            .when(cb.equal(root.get("workMode"), WorkMode.REMOTE), PLACE.get(PlaceFit.REMOTE))
            .when(cb.or(cb.isNull(root.get("latitude")), cb.isNull(root.get("longitude"))), PLACE.get(PlaceFit.UNKNOWN))
            .when(cb.le(squared, (double) NEAR_KM * NEAR_KM), PLACE.get(PlaceFit.NEAR))
            .when(cb.le(squared, (double) REACHABLE_KM * REACHABLE_KM), PLACE.get(PlaceFit.REACHABLE))
            .when(mostlyRemote, PLACE.get(PlaceFit.MOSTLY_REMOTE))
            .otherwise(PLACE.get(PlaceFit.FAR));
    }
}
