package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferPublisherBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfferPublisherBlockRepository extends JpaRepository<OfferPublisherBlock, Long> {

    List<OfferPublisherBlock> findByFreelanceIdOrderByNameAsc(Long freelanceId);

    Optional<OfferPublisherBlock> findByFreelanceIdAndPublisherKey(Long freelanceId, String publisherKey);

    Optional<OfferPublisherBlock> findByIdAndFreelanceId(Long id, Long freelanceId);

    long countByFreelanceId(Long freelanceId);
}
