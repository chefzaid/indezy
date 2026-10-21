package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferAppearance;
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
public interface OfferAppearanceRepository extends JpaRepository<OfferAppearance, Long> {

    /** Each of these missions (site and publisher key) with the references it was published under since {@code since}. */
    @Query("select a.site, a.publisherKey, count(a) from OfferAppearance a where a.publisherKey in :keys and a.firstSeenAt > :since " +
        "group by a.site, a.publisherKey")
    List<Object[]> countSince(@Param("keys") Collection<String> keys, @Param("since") Instant since);

    /** Which of these references of a site are already kept. */
    @Query("select a.externalId from OfferAppearance a where a.site = :site and a.externalId in :externalIds")
    List<String> findKnownIds(@Param("site") OfferSite site, @Param("externalIds") Collection<String> externalIds);

    @Modifying
    @Query("delete from OfferAppearance a where a.firstSeenAt < :before")
    int forgetBefore(@Param("before") Instant before);
}
