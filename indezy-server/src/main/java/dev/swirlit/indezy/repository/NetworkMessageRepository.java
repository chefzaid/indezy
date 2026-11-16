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

    /** The messages of these conversations matching a LIKE pattern on their text, case aside, the latest first. */
    @Query("select m from NetworkMessage m where m.conversationId in :ids and m.deletedAt is null and lower(m.text) like :pattern " +
        "escape '\\' order by m.id desc")
    List<NetworkMessage> search(@Param("ids") Collection<Long> conversationIds, @Param("pattern") String pattern, Pageable page);

    List<NetworkMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    /** How many messages others sent in a conversation between the two times, the deleted aside. */
    @Query("select count(m) from NetworkMessage m where m.conversationId = :conversationId and m.senderId <> :readerId " +
        "and m.deletedAt is null and (:after is null or m.createdAt > :after) and m.createdAt <= :before")
    long between(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId, @Param("after") LocalDateTime after,
                 @Param("before") LocalDateTime before);

    /** When the latest of those messages was sent. */
    @Query("select max(m.createdAt) from NetworkMessage m where m.conversationId = :conversationId and m.senderId <> :readerId " +
        "and m.deletedAt is null and (:after is null or m.createdAt > :after) and m.createdAt <= :before")
    LocalDateTime latestBetween(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId,
                                @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    long countBySenderIdAndCreatedAtAfter(Long senderId, LocalDateTime since);

    List<NetworkMessage> findBySenderIdOrderByCreatedAtAsc(Long senderId);
}
