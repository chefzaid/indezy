package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkRestriction;
import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkRestrictionRepository extends JpaRepository<NetworkRestriction, Long> {

    Optional<NetworkRestriction> findByOwnerIdAndTargetIdAndKind(Long ownerId, Long targetId, NetworkRestrictionKind kind);

    /** The members the account mutes or blocks, still in the network, the latest first. */
    @Query("select m from NetworkRestriction r join NetworkMember m on m.userId = r.targetId " +
        "where r.ownerId = :ownerId and r.kind = :kind and m.active = true order by r.createdAt desc")
    List<NetworkMember> targets(@Param("ownerId") Long ownerId, @Param("kind") NetworkRestrictionKind kind);

    /** The accounts that block the account, or that it blocks: the members it neither sees nor is seen by. */
    @Query("select case when r.ownerId = :userId then r.targetId else r.ownerId end from NetworkRestriction r " +
        "where r.kind = dev.swirlit.indezy.model.enums.NetworkRestrictionKind.BLOCK and (r.ownerId = :userId or r.targetId = :userId)")
    List<Long> blockedEitherWay(@Param("userId") Long userId);
}
