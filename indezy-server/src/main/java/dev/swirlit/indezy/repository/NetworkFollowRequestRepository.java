package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkFollowRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NetworkFollowRequestRepository extends JpaRepository<NetworkFollowRequest, Long> {

    Optional<NetworkFollowRequest> findByRequesterIdAndMemberId(Long requesterId, Long memberId);

    /** The requests a member has to answer, the oldest first. */
    List<NetworkFollowRequest> findByMemberIdOrderByCreatedAtAscIdAsc(Long memberId);

    /** The requests a member sent and that wait for an answer, the latest first. */
    List<NetworkFollowRequest> findByRequesterIdOrderByCreatedAtDescIdDesc(Long requesterId);
}
