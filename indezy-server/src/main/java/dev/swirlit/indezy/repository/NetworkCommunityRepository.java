package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkCommunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NetworkCommunityRepository extends JpaRepository<NetworkCommunity, Long> {

    Optional<NetworkCommunity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<NetworkCommunity> findAllByOrderByNameAsc();

    long countByCreatorId(Long creatorId);
}
