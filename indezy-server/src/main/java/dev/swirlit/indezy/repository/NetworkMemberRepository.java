package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NetworkMemberRepository extends JpaRepository<NetworkMember, Long> {

    Optional<NetworkMember> findByUserId(Long userId);

    Optional<NetworkMember> findByHandle(String handle);

    List<NetworkMember> findByActiveTrue();

    /** The members in the network whose card is private. */
    List<NetworkMember> findByActiveTrueAndPrivateCardTrue();
}
