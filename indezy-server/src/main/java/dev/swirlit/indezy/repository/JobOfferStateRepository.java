package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.model.JobOfferState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobOfferStateRepository extends JpaRepository<JobOfferState, Long> {

    Optional<JobOfferState> findByFreelanceIdAndOfferId(Long freelanceId, Long offerId);

    /** A workspace's state of each given offer it acted on. */
    @Query("select new dev.swirlit.indezy.dto.OfferStateDto(o.id, s.saved, s.hidden, " +
        "case when s.seenAt is null then false else true end, p.id, s.note, s.application) " +
        "from JobOfferState s join s.offer o left join s.importedProject p " +
        "where s.freelance.id = :freelanceId and o.id in :offerIds")
    List<OfferStateDto> findStates(@Param("freelanceId") Long freelanceId, @Param("offerIds") Collection<Long> offerIds);

    List<JobOfferState> findByFreelanceId(Long freelanceId);

    /** The offers, with their missions' copy groups, whose note in the workspace contains {@code pattern} (a {@code LIKE}). */
    @Query("select s.offer.id, s.offer.copyGroup from JobOfferState s where s.freelance.id = :freelanceId and s.noteKey like :pattern escape '\\'")
    List<Object[]> findNoted(@Param("freelanceId") Long freelanceId, @Param("pattern") String pattern);

    /** How many of each publisher's offers the workspace hid since {@code since}, by the name they were published under. */
    @Query("select s.offer.company, count(s) from JobOfferState s where s.freelance.id = :freelanceId and s.hidden = true " +
        "and s.updatedAt >= :since and s.offer.company is not null group by s.offer.company")
    List<Object[]> countHiddenByPublisher(@Param("freelanceId") Long freelanceId, @Param("since") java.time.LocalDateTime since);

    /** The search text of the offers the workspace hid since {@code since}, the latest first. */
    @Query("select s.offer.searchText from JobOfferState s where s.freelance.id = :freelanceId and s.hidden = true " +
        "and s.updatedAt >= :since and s.offer.searchText is not null order by s.updatedAt desc")
    List<String> findHiddenSearchTexts(@Param("freelanceId") Long freelanceId, @Param("since") java.time.LocalDateTime since,
                                       org.springframework.data.domain.Pageable page);

    /** The search text of the offers the workspace kept or applied to, the latest first. */
    @Query("select s.offer.searchText from JobOfferState s where s.freelance.id = :freelanceId " +
        "and (s.saved = true or s.application is not null) and s.offer.searchText is not null order by s.updatedAt desc")
    List<String> findKeptSearchTexts(@Param("freelanceId") Long freelanceId, org.springframework.data.domain.Pageable page);

    /** The publishers of the offers the workspace kept or applied to, ever. */
    @Query("select distinct s.offer.company from JobOfferState s where s.freelance.id = :freelanceId " +
        "and (s.saved = true or s.application is not null) and s.offer.company is not null")
    List<String> findKeptPublishers(@Param("freelanceId") Long freelanceId);

    @Modifying
    @Query("update JobOfferState s set s.importedProject = null where s.importedProject.id = :projectId")
    int clearImportedProject(@Param("projectId") Long projectId);

    @Modifying
    @Query("delete from JobOfferState s where s.offer.id in :offerIds")
    int deleteByOfferIdIn(@Param("offerIds") Collection<Long> offerIds);
}
