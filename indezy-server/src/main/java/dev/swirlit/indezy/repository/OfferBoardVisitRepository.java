package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferBoardVisit;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfferBoardVisitRepository extends JpaRepository<OfferBoardVisit, Long> {

    List<OfferBoardVisit> findByFreelanceIdOrderBySite(Long freelanceId);

    Optional<OfferBoardVisit> findByFreelanceIdAndSite(Long freelanceId, OfferLeftOutSite site);

    @Modifying
    @Query("delete from OfferBoardVisit v where v.freelance.id = :freelanceId and v.site = :site")
    int deleteByFreelanceIdAndSite(@Param("freelanceId") Long freelanceId, @Param("site") OfferLeftOutSite site);
}
