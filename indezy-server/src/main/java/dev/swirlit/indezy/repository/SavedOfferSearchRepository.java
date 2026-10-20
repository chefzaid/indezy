package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.SavedOfferSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedOfferSearchRepository extends JpaRepository<SavedOfferSearch, Long> {

    List<SavedOfferSearch> findByFreelanceIdOrderByNameAsc(Long freelanceId);

    Optional<SavedOfferSearch> findByIdAndFreelanceId(Long id, Long freelanceId);

    long countByFreelanceId(Long freelanceId);

    boolean existsByFreelanceIdAndNameIgnoreCase(Long freelanceId, String name);
}
