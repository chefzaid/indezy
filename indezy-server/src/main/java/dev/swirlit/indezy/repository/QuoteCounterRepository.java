package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.QuoteCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface QuoteCounterRepository extends JpaRepository<QuoteCounter, Long> {

    /** The workspace's quote counter of the year, locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QuoteCounter> findByFreelanceIdAndYear(Long freelanceId, int year);
}
