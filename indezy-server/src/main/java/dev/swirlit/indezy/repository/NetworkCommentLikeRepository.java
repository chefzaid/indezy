package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkCommentLikeRepository extends JpaRepository<NetworkCommentLike, Long> {

    Optional<NetworkCommentLike> findByCommentIdAndUserId(Long commentId, Long userId);

    /** The likes of a post's comments: how many each has, and which the reader gave, are read from them. */
    List<NetworkCommentLike> findByPostId(Long postId);

    List<NetworkCommentLike> findByCommentId(Long commentId);

    List<NetworkCommentLike> findByUserIdOrderByCreatedAtAsc(Long userId);

    @Modifying
    @Query("delete from NetworkCommentLike l where l.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    @Modifying
    @Query("delete from NetworkCommentLike l where l.commentId in :commentIds")
    void deleteByCommentIdIn(@Param("commentIds") Collection<Long> commentIds);
}
