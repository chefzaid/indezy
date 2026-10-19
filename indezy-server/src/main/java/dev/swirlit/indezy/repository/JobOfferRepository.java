package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, Long>, JpaSpecificationExecutor<JobOffer> {

    List<JobOffer> findBySiteAndExternalIdIn(OfferSite site, Collection<String> externalIds);

    @Query("select o.externalId from JobOffer o where o.site = :site and o.externalId in :externalIds")
    List<String> findKnownIds(@Param("site") OfferSite site, @Param("externalIds") Collection<String> externalIds);

    /** Offers of other sites sharing one of these keys: the candidates to be copies of an offer of {@code site}. */
    @Query("select o from JobOffer o where o.site <> :site and (o.publisherKey = :publisherKey or o.openingKey = :openingKey)")
    List<JobOffer> findCopyCandidates(@Param("site") OfferSite site, @Param("publisherKey") String publisherKey,
                                      @Param("openingKey") String openingKey);

    List<JobOffer> findByCopyGroupIn(Collection<Long> copyGroups);

    /** Offers stored before copies were told apart, which have no copy keys yet. */
    List<JobOffer> findByPublisherKeyIsNullOrderById(Pageable pageable);

    /** The offer with its copies on other sites. */
    default List<JobOffer> findCopyGroupOf(JobOffer offer) {
        return offer.getCopyGroup() == null ? List.of(offer) : findByCopyGroupIn(List.of(offer.getCopyGroup()));
    }

    /** The skills of the given offers in their order, as {@code [offerId, skill]} rows. */
    @Query("select o.id, s from JobOffer o join o.skills s where o.id in :ids order by o.id, index(s)")
    List<Object[]> findSkillsOf(@Param("ids") Collection<Long> ids);

    /** Offers still open per site, as {@code [site, count]} rows. */
    @Query("select o.site, count(o) from JobOffer o where o.expiresAt is null or o.expiresAt > :now group by o.site")
    List<Object[]> countOpenBySite(@Param("now") Instant now);

    /**
     * Open offers' most frequent skills starting with a prefix (folded to lower case), as
     * {@code [skill, count]} rows; spellings that differ only by case count as one skill.
     */
    @Query("select min(s), count(o) from JobOffer o join o.skills s " +
        "where (o.expiresAt is null or o.expiresAt > :now) and lower(s) like :prefix " +
        "group by lower(s) order by count(o) desc")
    List<Object[]> findTopSkills(@Param("now") Instant now, @Param("prefix") String prefix, Pageable page);

    /** Offers too old or expired that no workspace saved or imported. */
    @Query("select o.id from JobOffer o where (o.publishedAt < :publishedBefore or o.expiresAt < :now) " +
        "and not exists (select s.id from JobOfferState s where s.offer = o and (s.saved = true or s.importedProject is not null))")
    List<Long> findDisposableIds(@Param("publishedBefore") Instant publishedBefore, @Param("now") Instant now);

    @Modifying
    @Query(value = "delete from job_offer_skills where offer_id in (:ids)", nativeQuery = true)
    int deleteSkillsOf(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from JobOffer o where o.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);
}
