package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.util.TextFolding;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * The offer feed's filters and sort as one JPA specification. A filter on a value an offer does not
 * state keeps the offer for duration, start date and seniority (rarely given), and leaves it out for
 * location, work mode and remote days; offers without a rate are left out only on request.
 */
public final class JobOfferSpecifications {

    /** Where distances are measured from. */
    public record Origin(double latitude, double longitude) {
    }

    private static final double KM_PER_DEGREE_LATITUDE = 110.57;
    private static final double KM_PER_DEGREE_LONGITUDE_AT_EQUATOR = 111.32;
    private static final int MAX_TERMS = 8;

    private JobOfferSpecifications() {
    }

    public static Specification<JobOffer> matching(OfferSearchRequest request, Long freelanceId, Origin origin, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.addAll(view(request, freelanceId, root, query, cb, now));
            keywords(request.getQ()).forEach(term ->
                predicates.add(cb.like(root.get("searchText"), "%" + escapeLike(term) + "%", '\\')));
            rate(request, root, cb, predicates);
            place(request, origin, root, cb, predicates);
            work(request, root, cb, predicates);
            if (request.getPublishedWithinDays() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("publishedAt"),
                    now.minus(Duration.ofDays(request.getPublishedWithinDays()))));
            }
            if (!request.getSites().isEmpty()) {
                predicates.add(root.get("site").in(request.getSites()));
            }
            if (!request.getSeniorities().isEmpty()) {
                predicates.add(cb.or(cb.isNull(root.get("seniority")), root.get("seniority").in(request.getSeniorities())));
            }
            for (String skill : request.getSkills()) {
                if (skill != null && !skill.isBlank()) {
                    predicates.add(hasSkill(skill, root, query, cb));
                }
            }
            if (!isCountQuery(query)) {
                query.orderBy(order(request.getSort(), origin, root, cb));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** The folded words of a search, at least two characters each, at most {@value #MAX_TERMS}. */
    static List<String> keywords(String q) {
        String folded = TextFolding.words(q);
        if (folded == null || folded.isBlank()) {
            return List.of();
        }
        return Arrays.stream(folded.split(" ")).filter(term -> term.length() >= 2).distinct().limit(MAX_TERMS).toList();
    }

    private static List<Predicate> view(OfferSearchRequest request, Long freelanceId, Root<JobOffer> root,
                                        CriteriaQuery<?> query, CriteriaBuilder cb, Instant now) {
        List<Predicate> predicates = new ArrayList<>();
        if (request.getView() == OfferSearchRequest.View.FEED) {
            predicates.add(cb.or(cb.isNull(root.get("expiresAt")), cb.greaterThan(root.get("expiresAt"), now)));
            if (freelanceId != null) {
                predicates.add(cb.not(state(freelanceId, root, query, cb, state -> cb.isTrue(state.get("hidden")))));
            }
        } else if (freelanceId == null) {
            predicates.add(cb.disjunction());
        } else {
            String flag = request.getView() == OfferSearchRequest.View.SAVED ? "saved" : "hidden";
            predicates.add(state(freelanceId, root, query, cb, state -> cb.isTrue(state.get(flag))));
        }
        if (request.isUnseenOnly() && freelanceId != null) {
            predicates.add(cb.not(state(freelanceId, root, query, cb, state -> cb.isNotNull(state.get("seenAt")))));
        }
        return predicates;
    }

    private static void rate(OfferSearchRequest request, Root<JobOffer> root, CriteriaBuilder cb, List<Predicate> predicates) {
        Path<Integer> min = root.get("rateMin");
        Path<Integer> max = root.get("rateMax");
        Predicate unknown = cb.and(cb.isNull(min), cb.isNull(max));
        if (request.isRateKnown()) {
            predicates.add(cb.not(unknown));
        }
        if (request.getRateMin() != null) {
            Expression<Integer> best = cb.coalesce(max, min);
            predicates.add(cb.or(unknown, cb.greaterThanOrEqualTo(best, request.getRateMin())));
        }
        if (request.getRateMax() != null) {
            Expression<Integer> lowest = cb.coalesce(min, max);
            predicates.add(cb.or(unknown, cb.lessThanOrEqualTo(lowest, request.getRateMax())));
        }
    }

    private static void place(OfferSearchRequest request, Origin origin, Root<JobOffer> root, CriteriaBuilder cb,
                              List<Predicate> predicates) {
        List<Predicate> areas = new ArrayList<>();
        if (!request.getRegions().isEmpty()) {
            areas.add(root.get("regionCode").in(request.getRegions()));
        }
        if (!request.getDepartments().isEmpty()) {
            areas.add(root.get("departmentCode").in(request.getDepartments()));
        }
        if (!areas.isEmpty()) {
            predicates.add(cb.or(areas.toArray(Predicate[]::new)));
        }
        if (request.getRadiusKm() != null && origin != null) {
            double radius = request.getRadiusKm();
            predicates.add(cb.isNotNull(root.get("latitude")));
            predicates.add(cb.lessThanOrEqualTo(squaredDistance(origin, root, cb), radius * radius));
        }
    }

    private static void work(OfferSearchRequest request, Root<JobOffer> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (!request.getWorkModes().isEmpty()) {
            predicates.add(root.get("workMode").in(request.getWorkModes()));
        }
        if (request.getMinRemoteDays() != null && request.getMinRemoteDays() > 0) {
            predicates.add(cb.or(cb.equal(root.get("workMode"), WorkMode.REMOTE),
                cb.greaterThanOrEqualTo(root.get("remoteDaysPerWeek"), request.getMinRemoteDays())));
        }
        Path<Integer> duration = root.get("durationMonths");
        if (request.getDurationMin() != null) {
            predicates.add(cb.or(cb.isNull(duration), cb.greaterThanOrEqualTo(duration, request.getDurationMin())));
        }
        if (request.getDurationMax() != null) {
            predicates.add(cb.or(cb.isNull(duration), cb.lessThanOrEqualTo(duration, request.getDurationMax())));
        }
        if (request.getStartBefore() != null) {
            predicates.add(cb.or(cb.isTrue(root.get("startAsap")), cb.isNull(root.get("startDate")),
                cb.lessThanOrEqualTo(root.get("startDate"), request.getStartBefore())));
        }
    }

    private static Predicate hasSkill(String skill, Root<JobOffer> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> match = query.subquery(Long.class);
        Root<JobOffer> offer = match.from(JobOffer.class);
        Join<JobOffer, String> skills = offer.join("skills");
        match.select(offer.get("id")).where(cb.equal(offer, root),
            cb.equal(cb.lower(skills), skill.strip().toLowerCase(java.util.Locale.ROOT)));
        return cb.exists(match);
    }

    private static Predicate state(Long freelanceId, Root<JobOffer> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                   Function<Root<JobOfferState>, Predicate> condition) {
        Subquery<Long> match = query.subquery(Long.class);
        Root<JobOfferState> state = match.from(JobOfferState.class);
        match.select(state.get("id")).where(cb.equal(state.get("offer"), root),
            cb.equal(state.get("freelance").get("id"), freelanceId), condition.apply(state));
        return cb.exists(match);
    }

    /**
     * Squared distance in km² on a plane tangent at the origin: precise to a few percent within a few
     * hundred kilometres, and plain arithmetic that every database runs.
     */
    private static Expression<Double> squaredDistance(Origin origin, Root<JobOffer> root, CriteriaBuilder cb) {
        double kmPerDegreeLongitude = KM_PER_DEGREE_LONGITUDE_AT_EQUATOR * Math.cos(Math.toRadians(origin.latitude()));
        Expression<Double> north = cb.prod(cb.diff(root.<Double>get("latitude"), origin.latitude()), KM_PER_DEGREE_LATITUDE);
        Expression<Double> east = cb.prod(cb.diff(root.<Double>get("longitude"), origin.longitude()), kmPerDegreeLongitude);
        return cb.sum(cb.prod(north, north), cb.prod(east, east));
    }

    private static List<Order> order(OfferSearchRequest.Sort sort, Origin origin, Root<JobOffer> root, CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        if (sort == OfferSearchRequest.Sort.RATE) {
            Expression<Integer> rate = cb.coalesce(root.get("rateMax"), root.get("rateMin"));
            orders.add(cb.asc(cb.selectCase().when(cb.isNull(rate), 1).otherwise(0)));
            orders.add(cb.desc(rate));
        } else if (sort == OfferSearchRequest.Sort.DISTANCE && origin != null) {
            orders.add(cb.asc(cb.selectCase().when(cb.isNull(root.get("latitude")), 1).otherwise(0)));
            orders.add(cb.asc(squaredDistance(origin, root, cb)));
        }
        orders.add(cb.desc(root.get("publishedAt")));
        orders.add(cb.desc(root.get("id")));
        return orders;
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        return Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
    }

    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
