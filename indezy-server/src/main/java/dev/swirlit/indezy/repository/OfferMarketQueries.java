package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.WorkMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * The reads behind the market snapshot, over the offers a feed search finds (its specification). The specification
 * orders what it finds; these queries drop that order, as they read every offer or group them.
 */
@Repository
@RequiredArgsConstructor
public class OfferMarketQueries {

    /** What the snapshot is computed from, one per offer. */
    public record Figures(Integer rateMin, Integer rateMax, WorkMode workMode, Integer remoteDaysPerWeek, String company,
                          Instant publishedAt) {
    }

    private final EntityManager entityManager;

    public List<Figures> figures(Specification<JobOffer> found) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Figures> query = cb.createQuery(Figures.class);
        Root<JobOffer> root = query.from(JobOffer.class);
        Predicate where = found.toPredicate(root, query, cb);
        query.select(cb.construct(Figures.class, root.get("rateMin"), root.get("rateMax"), root.get("workMode"),
                root.get("remoteDaysPerWeek"), root.get("company"), root.get("publishedAt")))
            .where(where)
            .orderBy(List.of());
        return entityManager.createQuery(query).getResultList();
    }

    /** The found offers' most frequent skills; spellings that differ only by case count as one skill. */
    public List<OfferFacetDto> topSkills(Specification<JobOffer> found, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<JobOffer> root = query.from(JobOffer.class);
        Join<JobOffer, String> skill = root.join("skills");
        Predicate where = found.toPredicate(root, query, cb);
        Expression<Long> offers = cb.countDistinct(root);
        query.multiselect(cb.least(skill), offers)
            .where(where)
            .groupBy(cb.lower(skill))
            .orderBy(cb.desc(offers), cb.asc(cb.lower(skill)));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList().stream()
            .map(row -> new OfferFacetDto(row.get(0, String.class), row.get(1, Long.class)))
            .toList();
    }
}
