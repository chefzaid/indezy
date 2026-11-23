package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ScheduledContactEmail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScheduledContactEmailRepository extends JpaRepository<ScheduledContactEmail, Long> {

    /** The workspace's emails waiting to go, the soonest first. */
    @Query("select e from ScheduledContactEmail e join fetch e.contact c left join fetch c.client where e.freelanceId = :freelanceId order by e.sendAt asc, e.id asc")
    List<ScheduledContactEmail> findWaiting(@Param("freelanceId") Long freelanceId);

    long countByFreelanceId(Long freelanceId);

    Optional<ScheduledContactEmail> findByIdAndFreelanceId(Long id, Long freelanceId);

    /** The ids of the emails whose time came, not taken yet (or taken by a replica that stopped long ago). */
    @Query("select e.id from ScheduledContactEmail e where e.sendAt <= :now and (e.claimedAt is null or e.claimedAt < :stale) order by e.sendAt asc")
    List<Long> findDue(@Param("now") Instant now, @Param("stale") Instant stale, Pageable page);

    /** Takes an email to send; 1 for the replica that gets it, 0 for the others. */
    @Modifying
    @Query("update ScheduledContactEmail e set e.claimedAt = :now where e.id = :id and (e.claimedAt is null or e.claimedAt < :stale)")
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("stale") Instant stale);
}
