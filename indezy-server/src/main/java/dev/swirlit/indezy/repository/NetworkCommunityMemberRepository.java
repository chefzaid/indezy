package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkCommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkCommunityMemberRepository extends JpaRepository<NetworkCommunityMember, Long> {

    Optional<NetworkCommunityMember> findByCommunityIdAndUserId(Long communityId, Long userId);

    /** The members in a community (those removed aside), the earliest first. */
    List<NetworkCommunityMember> findByCommunityIdAndRemovedFalseOrderByCreatedAtAsc(Long communityId);

    /** The communities a member is in. */
    List<NetworkCommunityMember> findByUserIdAndRemovedFalse(Long userId);

    /** How many members each community has, as [communityId, count]. */
    @Query("select m.communityId, count(m) from NetworkCommunityMember m where m.removed = false and (m.pending is null or m.pending = false) " +
        "group by m.communityId")
    List<Object[]> sizes();

    @Modifying
    @Query("delete from NetworkCommunityMember m where m.communityId = :communityId")
    void deleteByCommunityId(@Param("communityId") Long communityId);
}
