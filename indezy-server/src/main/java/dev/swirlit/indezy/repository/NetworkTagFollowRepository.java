package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkTagFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NetworkTagFollowRepository extends JpaRepository<NetworkTagFollow, Long> {

    List<NetworkTagFollow> findByUserIdOrderByTagAsc(Long userId);

    Optional<NetworkTagFollow> findByUserIdAndTag(Long userId, String tag);
}
