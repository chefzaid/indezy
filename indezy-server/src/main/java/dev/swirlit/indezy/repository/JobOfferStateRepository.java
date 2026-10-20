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
        "case when s.seenAt is null then false else true end, p.id, s.note) " +
        "from JobOfferState s join s.offer o left join s.importedProject p " +
        "where s.freelance.id = :freelanceId and o.id in :offerIds")
    List<OfferStateDto> findStates(@Param("freelanceId") Long freelanceId, @Param("offerIds") Collection<Long> offerIds);

    List<JobOfferState> findByFreelanceId(Long freelanceId);

    @Modifying
    @Query("update JobOfferState s set s.importedProject = null where s.importedProject.id = :projectId")
    int clearImportedProject(@Param("projectId") Long projectId);

    @Modifying
    @Query("delete from JobOfferState s where s.offer.id in :offerIds")
    int deleteByOfferIdIn(@Param("offerIds") Collection<Long> offerIds);
}
