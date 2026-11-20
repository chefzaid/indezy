package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkMeetupAttendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkMeetupAttendeeRepository extends JpaRepository<NetworkMeetupAttendee, Long> {

    Optional<NetworkMeetupAttendee> findByMeetupIdAndUserId(Long meetupId, Long userId);

    List<NetworkMeetupAttendee> findByMeetupIdOrderByCreatedAtAsc(Long meetupId);

    List<NetworkMeetupAttendee> findByUserId(Long userId);

    /** Each event's answers, as (event id, status, count) rows. */
    @Query("select a.meetupId, a.status, count(a) from NetworkMeetupAttendee a group by a.meetupId, a.status")
    List<Object[]> counts();

    @Modifying
    @Query("delete from NetworkMeetupAttendee a where a.meetupId = :meetupId")
    void deleteByMeetupId(@Param("meetupId") Long meetupId);
}
