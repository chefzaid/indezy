package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferSuggestionDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfferSuggestionDismissalRepository extends JpaRepository<OfferSuggestionDismissal, Long> {

    @Query("select d.suggestionKey from OfferSuggestionDismissal d where d.freelance.id = :freelanceId")
    List<String> findKeys(@Param("freelanceId") Long freelanceId);

    boolean existsByFreelanceIdAndSuggestionKey(Long freelanceId, String suggestionKey);

    /** The workspace's dismissals, the oldest first. */
    List<OfferSuggestionDismissal> findByFreelanceIdOrderByDismissedAtAsc(Long freelanceId);
}
