package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferSkip;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface OfferSkipRepository extends JpaRepository<OfferSkip, Long> {

    @Query("select s.externalId from OfferSkip s where s.site = :site and s.externalId in :externalIds")
    List<String> findSkippedIds(@Param("site") OfferSite site, @Param("externalIds") Collection<String> externalIds);

    @Modifying
    @Query("delete from OfferSkip s where s.skippedAt < :before")
    int deleteSkippedBefore(@Param("before") Instant before);
}
