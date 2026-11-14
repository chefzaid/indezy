package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NetworkCommentRepository extends JpaRepository<NetworkComment, Long> {

    /** The comments and replies under a post, the oldest first. */
    List<NetworkComment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId);

    List<NetworkComment> findByPostIdIn(Collection<Long> postIds);

    List<NetworkComment> findByParentId(Long parentId);

    @Modifying
    @Query("delete from NetworkComment c where c.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
