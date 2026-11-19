package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkPostImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NetworkPostImpressionRepository extends JpaRepository<NetworkPostImpression, Long> {

    List<NetworkPostImpression> findByViewerIdAndPostIdIn(Long viewerId, Collection<Long> postIds);

    /** How many times a post was shown, and to how many members. */
    @Query("select coalesce(sum(i.times), 0), count(i) from NetworkPostImpression i where i.postId = :postId")
    List<Object[]> totals(@Param("postId") Long postId);

    /** How many members were shown the author's posts since a time. */
    @Query("select count(i) from NetworkPostImpression i where i.lastSeenAt >= :since and i.postId in " +
        "(select p.id from NetworkPost p where p.authorId = :authorId)")
    long reachedSince(@Param("authorId") Long authorId, @Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from NetworkPostImpression i where i.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
