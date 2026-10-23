package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.SavedOfferSearch;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SavedOfferSearchRepository extends JpaRepository<SavedOfferSearch, Long> {

    List<SavedOfferSearch> findByFreelanceIdOrderByNameAsc(Long freelanceId);

    Optional<SavedOfferSearch> findByIdAndFreelanceId(Long id, Long freelanceId);

    long countByFreelanceId(Long freelanceId);

    boolean existsByFreelanceIdAndNameIgnoreCase(Long freelanceId, String name);

    /** The searches that email their offers. */
    @Query("select s.id from SavedOfferSearch s where s.emailFrequency in :frequencies order by s.id")
    List<Long> findIdsByEmailFrequencyIn(@Param("frequencies") Collection<SavedSearchEmails> frequencies);

    Optional<SavedOfferSearch> findByEmailToken(String emailToken);
}
