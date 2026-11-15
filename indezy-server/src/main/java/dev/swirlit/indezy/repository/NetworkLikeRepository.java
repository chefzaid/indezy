package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkLike;
import dev.swirlit.indezy.model.NetworkMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkLikeRepository extends JpaRepository<NetworkLike, Long> {

    Optional<NetworkLike> findByPostIdAndUserId(Long postId, Long userId);

    List<NetworkLike> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** The likes of these posts: how many each has, and which the reader gave, are read from them. */
    List<NetworkLike> findByPostIdIn(Collection<Long> postIds);

    /** The members, still in the network, who like the post, the latest first. */
    @Query("select m from NetworkLike l join NetworkMember m on m.userId = l.userId " +
        "where l.postId = :postId and m.active = true order by l.createdAt desc")
    List<NetworkMember> likers(@Param("postId") Long postId);

    @Modifying
    @Query("delete from NetworkLike l where l.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
