package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkMemberRepository extends JpaRepository<NetworkMember, Long> {

    /** Marks these members seen now: they have the app open. */
    @Modifying
    @Query("update NetworkMember m set m.lastSeenAt = :now where m.userId in :userIds")
    int markSeen(@Param("userIds") Collection<Long> userIds, @Param("now") Instant now);

    Optional<NetworkMember> findByUserId(Long userId);

    Optional<NetworkMember> findByHandle(String handle);

    List<NetworkMember> findByActiveTrue();

    /** The members in the network whose card is private. */
    List<NetworkMember> findByActiveTrueAndPrivateCardTrue();
}
