package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OfferChangeRepository extends JpaRepository<OfferChange, Long> {

    /** The changes of these offers, the latest first. */
    @Query("select c from OfferChange c where c.offer.id in :offerIds order by c.changedAt desc, c.id desc")
    List<OfferChange> findByOfferIds(@Param("offerIds") Collection<Long> offerIds);

    @Modifying
    @Query("delete from OfferChange c where c.offer.id in :offerIds")
    int deleteByOfferIdIn(@Param("offerIds") Collection<Long> offerIds);
}
