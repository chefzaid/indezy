package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkPollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkPollVoteRepository extends JpaRepository<NetworkPollVote, Long> {

    Optional<NetworkPollVote> findByPostIdAndUserId(Long postId, Long userId);

    List<NetworkPollVote> findByPostIdIn(Collection<Long> postIds);

    @Modifying
    @Query("delete from NetworkPollVote v where v.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
