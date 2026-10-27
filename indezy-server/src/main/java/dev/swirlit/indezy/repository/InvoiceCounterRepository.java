package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.InvoiceCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, Long> {

    /** The workspace's counter of the year, locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvoiceCounter> findByFreelanceIdAndYear(Long freelanceId, int year);
}
