package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkMissionTake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkMissionTakeRepository extends JpaRepository<NetworkMissionTake, Long> {

    Optional<NetworkMissionTake> findByPostIdAndUserId(Long postId, Long userId);

    List<NetworkMissionTake> findByPostIdIn(Collection<Long> postIds);

    List<NetworkMissionTake> findByPostIdAndNamedTrueOrderByCreatedAtAsc(Long postId);

    @Modifying
    @Query("delete from NetworkMissionTake t where t.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    /** How many members took the author's missions since a time. */
    @Query("select count(t) from NetworkMissionTake t where t.createdAt >= :since and t.postId in " +
        "(select p.id from NetworkPost p where p.authorId = :authorId)")
    long takenSince(@Param("authorId") Long authorId, @Param("since") LocalDateTime since);
}
