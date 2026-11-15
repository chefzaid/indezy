package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkUseful;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkUsefulRepository extends JpaRepository<NetworkUseful, Long> {

    Optional<NetworkUseful> findByPostIdAndUserId(Long postId, Long userId);

    List<NetworkUseful> findByPostIdIn(Collection<Long> postIds);

    List<NetworkUseful> findByUserIdOrderByCreatedAtAsc(Long userId);

    @Modifying
    @Query("delete from NetworkUseful u where u.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
