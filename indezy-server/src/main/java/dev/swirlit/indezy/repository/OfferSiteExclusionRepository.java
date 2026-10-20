package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferSiteExclusion;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfferSiteExclusionRepository extends JpaRepository<OfferSiteExclusion, Long> {

    @Query("select e.site from OfferSiteExclusion e where e.freelance.id = :freelanceId order by e.site")
    List<OfferSite> findSitesByFreelanceId(@Param("freelanceId") Long freelanceId);

    boolean existsByFreelanceIdAndSite(Long freelanceId, OfferSite site);

    @Modifying
    @Query("delete from OfferSiteExclusion e where e.freelance.id = :freelanceId and e.site = :site")
    int deleteByFreelanceIdAndSite(@Param("freelanceId") Long freelanceId, @Param("site") OfferSite site);
}
