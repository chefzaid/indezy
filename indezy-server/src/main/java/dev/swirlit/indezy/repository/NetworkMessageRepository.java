package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NetworkMessageRepository extends JpaRepository<NetworkMessage, Long> {

    /** A conversation's messages before the given id (all with none), the newest first. */
    @Query("select m from NetworkMessage m where m.conversationId = :conversationId and (:before is null or m.id < :before) " +
        "order by m.id desc")
    List<NetworkMessage> page(@Param("conversationId") Long conversationId, @Param("before") Long before, Pageable page);

    /** The latest message of each of these conversations. */
    @Query("select m from NetworkMessage m where m.id in (select max(n.id) from NetworkMessage n where n.conversationId in :ids " +
        "group by n.conversationId)")
    List<NetworkMessage> latest(@Param("ids") Collection<Long> conversationIds);

    /** How many messages others sent in a conversation after the given time (all with none). */
    @Query("select count(m) from NetworkMessage m where m.conversationId = :conversationId and m.senderId <> :readerId " +
        "and (:since is null or m.createdAt > :since)")
    long unread(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId, @Param("since") LocalDateTime since);

    long countBySenderIdAndCreatedAtAfter(Long senderId, LocalDateTime since);

    List<NetworkMessage> findBySenderIdOrderByCreatedAtAsc(Long senderId);
}
