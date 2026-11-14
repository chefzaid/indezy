package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findByFreelanceIdOrderByCreatedAtDesc(Long freelanceId);

    Optional<Quote> findByIdAndFreelanceId(Long id, Long freelanceId);
}
