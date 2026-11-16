package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkConversationMemberRepository extends JpaRepository<NetworkConversationMember, Long> {

    List<NetworkConversationMember> findByUserId(Long userId);

    List<NetworkConversationMember> findByConversationId(Long conversationId);

    List<NetworkConversationMember> findByConversationIdIn(Collection<Long> conversationIds);

    Optional<NetworkConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);
}
