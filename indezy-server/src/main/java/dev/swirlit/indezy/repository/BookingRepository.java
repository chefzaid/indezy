package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** The calls booked since the given time whose email is still to send, the oldest first. */
    @Query("SELECT b.id FROM Booking b WHERE b.noticeSentAt IS NULL AND b.cancelledAt IS NULL AND b.createdAt > :since ORDER BY b.id")
    List<Long> findIdsAwaitingNotice(@Param("since") LocalDateTime since);

    /** Claims the call's email for sending: 1 when this caller claimed it, 0 when another replica already did. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Booking b SET b.noticeSentAt = :now WHERE b.id = :id AND b.noticeSentAt IS NULL")
    int claimNotice(@Param("id") Long id, @Param("now") Instant now);

    /** Gives a claimed email back, to send again later. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Booking b SET b.noticeSentAt = NULL WHERE b.id = :id")
    int releaseNotice(@Param("id") Long id);
}
