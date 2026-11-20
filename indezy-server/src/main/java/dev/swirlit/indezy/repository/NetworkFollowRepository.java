package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkFollow;
import dev.swirlit.indezy.model.NetworkMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NetworkFollowRepository extends JpaRepository<NetworkFollow, Long> {

    Optional<NetworkFollow> findByFollowerIdAndFollowedId(Long followerId, Long followedId);

    List<NetworkFollow> findByFollowerId(Long followerId);

    long countByFollowerIdAndCreatedAtAfter(Long followerId, LocalDateTime since);

    /** The members, still in the network, who follow the account, the latest first. */
    @Query("select m from NetworkFollow f join NetworkMember m on m.userId = f.followerId " +
        "where f.followedId = :userId and m.active = true order by f.createdAt desc")
    List<NetworkMember> followers(@Param("userId") Long userId);

    /** The members, still in the network, the account follows, the latest first. */
    @Query("select m from NetworkFollow f join NetworkMember m on m.userId = f.followedId " +
        "where f.followerId = :userId and m.active = true order by f.createdAt desc")
    List<NetworkMember> following(@Param("userId") Long userId);

    long countByFollowedIdAndCreatedAtAfter(Long followedId, LocalDateTime since);

    /** Who began following a member since a moment, the latest first. */
    List<NetworkFollow> findByFollowedIdAndCreatedAtAfterOrderByCreatedAtDesc(Long followedId, LocalDateTime since);
}
