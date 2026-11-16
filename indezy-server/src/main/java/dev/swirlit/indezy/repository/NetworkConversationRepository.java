package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NetworkConversationRepository extends JpaRepository<NetworkConversation, Long> {

    Optional<NetworkConversation> findByPairKey(String pairKey);

    long countByCreatedByAndCreatedAtAfter(Long createdBy, LocalDateTime since);
}
