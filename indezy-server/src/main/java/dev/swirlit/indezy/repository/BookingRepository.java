package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** The workspace's calls ending after the given time, cancelled ones included, the soonest first. */
    List<Booking> findByFreelanceIdAndEndsAtAfterOrderByStartsAtAsc(Long freelanceId, Instant after);

    /** Every call of the workspace, for the account data export. */
    List<Booking> findByFreelanceIdOrderByStartsAtAsc(Long freelanceId);

    Optional<Booking> findByIdAndFreelanceId(Long id, Long freelanceId);

    long countByFreelanceIdAndCreatedAtAfter(Long freelanceId, LocalDateTime after);
}
