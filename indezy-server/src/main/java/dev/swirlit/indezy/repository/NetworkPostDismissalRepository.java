package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkPostDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkPostDismissalRepository extends JpaRepository<NetworkPostDismissal, Long> {

    Optional<NetworkPostDismissal> findByUserIdAndPostId(Long userId, Long postId);

    /** The member's latest 200 dismissals, the latest first. */
    List<NetworkPostDismissal> findTop200ByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("delete from NetworkPostDismissal d where d.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
