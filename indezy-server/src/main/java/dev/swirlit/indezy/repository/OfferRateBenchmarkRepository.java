package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferRateBenchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfferRateBenchmarkRepository extends JpaRepository<OfferRateBenchmark, Long> {
}
