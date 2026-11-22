package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferVerdict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OfferVerdictRepository extends JpaRepository<OfferVerdict, Long> {

    Optional<OfferVerdict> findByFreelanceIdAndOfferId(Long freelanceId, Long offerId);

    /** The verdicts of offers the feed no longer keeps. */
    @Modifying
    @Transactional
    @Query("DELETE FROM OfferVerdict v WHERE NOT EXISTS (SELECT o.id FROM JobOffer o WHERE o.id = v.offerId)")
    int deleteOrphans();
}
