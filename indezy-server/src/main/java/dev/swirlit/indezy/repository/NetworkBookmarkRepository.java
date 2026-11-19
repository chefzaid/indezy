package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkBookmarkRepository extends JpaRepository<NetworkBookmark, Long> {

    Optional<NetworkBookmark> findByUserIdAndPostId(Long userId, Long postId);

    /** The member's saved posts, the latest saved first. */
    List<NetworkBookmark> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** Which of these posts the member saved. */
    List<NetworkBookmark> findByUserIdAndPostIdIn(Long userId, Collection<Long> postIds);

    @Modifying
    @Query("delete from NetworkBookmark b where b.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    long countByPostId(Long postId);
}
