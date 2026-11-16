package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkConversationMemberRepository extends JpaRepository<NetworkConversationMember, Long> {

    List<NetworkConversationMember> findByUserId(Long userId);

    /** The members with messages of others, unread and not emailed yet, sent before the given time, the muted aside. */
    @Query("select distinct p.userId from NetworkConversationMember p, NetworkMessage m where m.conversationId = p.conversationId " +
        "and m.senderId <> p.userId and m.deletedAt is null and (p.muted is null or p.muted = false) and m.createdAt <= :before " +
        "and (p.lastReadAt is null or m.createdAt > p.lastReadAt) and (p.emailedUntil is null or m.createdAt > p.emailedUntil)")
    List<Long> withUnemailedMessages(@Param("before") LocalDateTime before);

    List<NetworkConversationMember> findByConversationId(Long conversationId);

    List<NetworkConversationMember> findByConversationIdIn(Collection<Long> conversationIds);

    Optional<NetworkConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);
}
