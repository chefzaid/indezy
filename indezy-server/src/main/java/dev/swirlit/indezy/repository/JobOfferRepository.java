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

    /** Each open offer stating a daily rate, with its mission (copy group), region, rates and first skill. */
    @Query("select o.id, o.copyGroup, o.regionCode, o.rateMin, o.rateMax, s from JobOffer o join o.skills s " +
        "where index(s) = 0 and (o.rateMin is not null or o.rateMax is not null) and (o.expiresAt is null or o.expiresAt > :now)")
    List<Object[]> findRatedMainSkills(@Param("now") Instant now);

    /** The newest offers of {@code site} whose address holds {@code fragment}, case aside: the candidates for a pasted link. */
    List<JobOffer> findTop20BySiteAndUrlContainingIgnoreCaseOrderByIdDesc(OfferSite site, String fragment);

    @Query("select o.externalId from JobOffer o where o.site = :site and o.externalId in :externalIds")
    List<String> findKnownIds(@Param("site") OfferSite site, @Param("externalIds") Collection<String> externalIds);

    /** Offers of other sites sharing one of these keys: the candidates to be copies of an offer of {@code site}. */
    @Query("select o from JobOffer o where o.site <> :site and (o.publisherKey = :publisherKey or o.openingKey = :openingKey)")
    List<JobOffer> findCopyCandidates(@Param("site") OfferSite site, @Param("publisherKey") String publisherKey,
                                      @Param("openingKey") String openingKey);

    List<JobOffer> findByCopyGroupIn(Collection<Long> copyGroups);

    /** Offers stored before copies were told apart, which have no copy keys yet. */
    List<JobOffer> findByPublisherKeyIsNullOrderById(Pageable pageable);

    /** Offers after {@code afterId} with a description and no work mode. */
    @Query("select o from JobOffer o where o.id > :afterId and o.workMode is null and o.description is not null order by o.id")
    List<JobOffer> findWithoutWorkModeAfter(@Param("afterId") Long afterId, Pageable pageable);

    /** Offers after {@code afterId} that name a French (or unknown) place and have no coordinates. */
    @Query("select o from JobOffer o where o.id > :afterId and o.latitude is null and (o.city is not null or o.postalCode is not null) " +
        "and (o.countryCode is null or o.countryCode = 'FR') order by o.id")
    List<JobOffer> findUnlocatedAfter(@Param("afterId") Long afterId, Pageable pageable);

    /** Offers after {@code afterId} whose description may still hold markup (see {@code HtmlText.hasEscapedMarkup}). */
    @Query("select o from JobOffer o where o.id > :afterId and o.description like '%<%>%' order by o.id")
    List<JobOffer> findWithMarkupAfter(@Param("afterId") Long afterId, Pageable pageable);

    /** Offers stored before publishers had a key, from {@code afterId} on (a name without words never gets one). */
    List<JobOffer> findByCompanyIsNotNullAndCompanyKeyIsNullAndIdGreaterThanOrderById(Long afterId, Pageable pageable);

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
     * What each site brought, as {@code [site, offers first seen since day, since week, newest publication]} rows; a
     * site without offers has none.
     */
    @Query("select o.site, sum(case when o.firstSeenAt >= :day then 1 else 0 end), " +
        "sum(case when o.firstSeenAt >= :week then 1 else 0 end), max(o.publishedAt) from JobOffer o group by o.site")
    List<Object[]> collectionBySite(@Param("day") Instant day, @Param("week") Instant week);

    /**
     * Open offers' most frequent skills starting with a prefix (folded to lower case), as
     * {@code [skill, count]} rows; spellings that differ only by case count as one skill.
     */
    @Query("select min(s), count(o) from JobOffer o join o.skills s " +
        "where (o.expiresAt is null or o.expiresAt > :now) and lower(s) like :prefix " +
        "group by lower(s) order by count(o) desc")
    List<Object[]> findTopSkills(@Param("now") Instant now, @Param("prefix") String prefix, Pageable page);

    /** Offers too old or expired that no workspace saved, applied to or imported. */
    @Query("select o.id from JobOffer o where (o.publishedAt < :publishedBefore or o.expiresAt < :now) " +
        "and not exists (select s.id from JobOfferState s where s.offer = o and (s.saved = true or s.importedProject is not null " +
        "or s.application.appliedOn is not null))")
    List<Long> findDisposableIds(@Param("publishedBefore") Instant publishedBefore, @Param("now") Instant now);

    @Modifying
    @Query(value = "delete from job_offer_skills where offer_id in (:ids)", nativeQuery = true)
    int deleteSkillsOf(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from JobOffer o where o.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);
}
