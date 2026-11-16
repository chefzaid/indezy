package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkMessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkMessageReactionRepository extends JpaRepository<NetworkMessageReaction, Long> {

    List<NetworkMessageReaction> findByMessageIdIn(Collection<Long> messageIds);

    Optional<NetworkMessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);
}
