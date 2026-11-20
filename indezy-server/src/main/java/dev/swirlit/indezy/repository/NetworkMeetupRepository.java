package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkMeetup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NetworkMeetupRepository extends JpaRepository<NetworkMeetup, Long> {

    /** The events starting after a moment, the soonest first, 1,000 at most. */
    List<NetworkMeetup> findTop1000ByStartsAtAfterOrderByStartsAtAscIdAsc(LocalDateTime after);

    /** A member's events, the latest first. */
    List<NetworkMeetup> findTop200ByOrganizerIdOrderByStartsAtDescIdDesc(Long organizerId);

    /** The events starting in a window: those whose attendees are reminded. */
    List<NetworkMeetup> findByStartsAtBetween(LocalDateTime from, LocalDateTime to);

    long countByOrganizerIdAndCreatedAtAfter(Long organizerId, LocalDateTime since);
}
