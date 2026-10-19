package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface OfferSiteStatusRepository extends JpaRepository<OfferSiteStatus, OfferSite> {

    /**
     * Marks a due site as running; 1 when this caller won it, 0 when it is not due or another run
     * holds it (a run older than {@code staleBefore} is taken over, as its server died mid-run).
     */
    @Modifying
    @Query("update OfferSiteStatus s set s.runningSince = :now where s.site = :site and s.nextRunAt <= :now " +
        "and (s.runningSince is null or s.runningSince < :staleBefore)")
    int claim(@Param("site") OfferSite site, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
