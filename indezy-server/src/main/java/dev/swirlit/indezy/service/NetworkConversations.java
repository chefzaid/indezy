package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkConversation;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.model.enums.NetworkMessageRequestStatus;
import dev.swirlit.indezy.repository.NetworkConversationMemberRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.realtime.NetworkPresence;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What conversations share (see docs/features.md#member-network): who is in one, who may still write in it, and how it
 * reads in the list. A conversation with a member who left, or with whom either blocks the other, is hidden: not found.
 */
@Component
@RequiredArgsConstructor
class NetworkConversations {

    static final String NOT_FOUND = "No such conversation";
    /** How long after sending a message its sender can still edit or delete it. */
    static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
    static final int PREVIEW = 80;

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkFollowRepository follows;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkConversationMemberRepository participants;
    private final NetworkMessageRepository messages;
    private final Clock clock;
    private final NetworkPresence presence;

    /** The reader's place in the conversation; not found when they are not in it. */
    NetworkConversationMember mine(Long readerId, Long conversationId) {
        return participants.findByConversationIdAndUserId(conversationId, readerId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /**
     * How a conversation reads for one of its members: between two, the other member (none when out of the reader's reach:
     * left, or either blocks the other); a group, its name and its other members in reach.
     */
    record Seen(NetworkConversation conversation, Long otherId, NetworkDtos.MemberCard with, NetworkMessageDtos.Group group) {

        /** The other member's name, or the group's; none for a former member (who left, is on hold or was deleted). */
        String label() {
            return group != null ? group.title() : with == null ? null : (with.firstName() + " " + with.lastName()).strip();
        }

        /** The other member's handle; none for a group. */
        String handle() {
            return with == null ? null : with.handle();
        }
    }

    /** How the conversation reads for the reader; none when it is out of their reach. */
    Optional<Seen> seen(Long readerId, NetworkConversation conversation) {
        List<Long> everyone = participants.findByConversationId(conversation.getId()).stream().map(NetworkConversationMember::getUserId).toList();
        // Never read by a member outside it, whatever its kind.
        if (!everyone.contains(readerId)) {
            return Optional.empty();
        }
        if (!conversation.isGroup()) {
            // The other member left, is on hold or was deleted: the past stays readable, from a former member; a block
            // either way hides it.
            Long other = everyone.stream().filter(userId -> !userId.equals(readerId)).findFirst().orElse(null);
            if (other != null && restrictions.blockedEitherWay(readerId).contains(other)) {
                return Optional.empty();
            }
            return Optional.of(new Seen(conversation, other, other == null ? null : visible(readerId, other).orElse(null), null));
        }
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(readerId));
        List<NetworkDtos.MemberCard> others = everyone.stream().filter(userId -> !userId.equals(readerId) && !blocked.contains(userId))
            .flatMap(userId -> card(userId).stream()).toList();
        return Optional.of(new Seen(conversation, null, null, new NetworkMessageDtos.Group(conversation.getTitle(), others, everyone.size(),
            network.handle(conversation.getCreatedBy()).orElse(null), conversation.getCreatedBy().equals(readerId))));
    }

    /** A member's card while they are in the network. */
    Optional<NetworkDtos.MemberCard> card(Long userId) {
        return members.findByUserId(userId).filter(NetworkMember::isActive).flatMap(network::liveCard);
    }

    /** The other member of a conversation between two. */
    Long other(Long conversationId, Long readerId) {
        return participants.findByConversationId(conversationId).stream().map(NetworkConversationMember::getUserId)
            .filter(userId -> !userId.equals(readerId)).findFirst().orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** The other member's card, while they are in the network and neither blocks the other; none otherwise. */
    Optional<NetworkDtos.MemberCard> visible(Long readerId, Long otherId) {
        if (restrictions.blockedEitherWay(readerId).contains(otherId)) {
            return Optional.empty();
        }
        return members.findByUserId(otherId).filter(NetworkMember::isActive).flatMap(network::liveCard);
    }

    /**
     * Why the sender cannot write to the recipient, before any conversation between them: the recipient left, either blocks
     * the other, or neither follows the other (a follow counts once accepted, a request waiting does not) and the recipient
     * takes no message requests.
     */
    Optional<NetworkMessageDtos.Closed> closed(Long senderId, Long recipientId) {
        return closed(senderId, recipientId, null);
    }

    /**
     * Why the sender cannot write to the recipient in their conversation (none yet when null): as above, unless it is a
     * message request. A request accepted opens it; one waiting lets its sender write one message, then waits for the
     * recipient, who answers before writing; one declined reads to its sender as still waiting.
     */
    Optional<NetworkMessageDtos.Closed> closed(Long senderId, Long recipientId, NetworkConversation conversation) {
        // A member who left, or whose account is on hold before its deletion, reads as gone.
        if (members.findByUserId(recipientId).filter(NetworkMember::isActive).flatMap(network::liveCard).isEmpty()) {
            return Optional.of(NetworkMessageDtos.Closed.LEFT);
        }
        if (restrictions.blockedEitherWay(senderId).contains(recipientId)) {
            return Optional.of(NetworkMessageDtos.Closed.BLOCKED);
        }
        if (connected(senderId, recipientId)) {
            return Optional.empty();
        }
        NetworkMessageRequestStatus status = conversation == null ? null : conversation.getRequestStatus();
        if (status == NetworkMessageRequestStatus.ACCEPTED) {
            return Optional.empty();
        }
        if (status != null) {
            if (!conversation.getCreatedBy().equals(senderId)) {
                return Optional.of(status == NetworkMessageRequestStatus.PENDING ? NetworkMessageDtos.Closed.REQUEST_RECEIVED :
                    NetworkMessageDtos.Closed.NOT_FOLLOWED);
            }
            boolean first = status == NetworkMessageRequestStatus.PENDING && !messages.existsByConversationIdAndSenderId(conversation.getId(), senderId);
            return first ? Optional.empty() : Optional.of(NetworkMessageDtos.Closed.REQUEST_SENT);
        }
        // No conversation yet: one opened would be a request, if the recipient takes them.
        boolean requestable = conversation == null && members.findByUserId(recipientId).filter(NetworkMember::acceptsMessageRequests).isPresent();
        return requestable ? Optional.empty() : Optional.of(NetworkMessageDtos.Closed.NOT_FOLLOWED);
    }

    /** Whether either member follows the other, a follow counting once accepted. */
    boolean connected(Long first, Long second) {
        return follows.findByFollowerIdAndFollowedId(first, second).isPresent() || follows.findByFollowerIdAndFollowedId(second, first).isPresent();
    }

    /**
     * Whether the conversation is a message request the reader received and has not accepted: waiting for their answer, or
     * declined. It stays out of their conversations, a waiting one listed among their requests; a follow either way since
     * makes it an ordinary conversation.
     */
    boolean requestTo(Long readerId, NetworkConversation conversation) {
        NetworkMessageRequestStatus status = conversation.getRequestStatus();
        return (status == NetworkMessageRequestStatus.PENDING || status == NetworkMessageRequestStatus.DECLINED) &&
            !conversation.getCreatedBy().equals(readerId) && !connected(readerId, conversation.getCreatedBy());
    }

    /** The conversation as its reader's list shows it. */
    NetworkMessageDtos.Conversation view(Seen seen, NetworkConversationMember mine, NetworkMessage last) {
        NetworkConversation conversation = seen.conversation();
        NetworkMessageDtos.Preview preview = last == null ? null : new NetworkMessageDtos.Preview(last.getSenderId().equals(mine.getUserId()),
            last.isDeleted() ? "" : excerpt(last.getText()), last.isDeleted(), last.getCreatedAt());
        int unread = (int) messages.unread(conversation.getId(), mine.getUserId(), mine.getLastReadAt());
        // In a group, its members write while they are in it.
        NetworkMessageDtos.Closed closed = seen.group() != null ? null : closed(mine.getUserId(), seen.otherId(), conversation).orElse(null);
        return new NetworkMessageDtos.Conversation(conversation.getId(), seen.with(), seen.group(), preview, unread, mine.isMuted(),
            mine.isArchived(), closed, conversation.getLastMessageAt(), seenAt(seen, mine), online(seen, mine));
    }

    /** Whether the other member of a conversation between two has the app open, while both show it. */
    private boolean online(Seen seen, NetworkConversationMember mine) {
        if (seen.group() != null || seen.otherId() == null || seen.with() == null) {
            return false;
        }
        return presence.online(members.findByUserId(mine.getUserId()).orElse(null), members.findByUserId(seen.otherId()).orElse(null));
    }

    /** When the other member of a conversation between two last read it, while both show read receipts; none in a group. */
    private LocalDateTime seenAt(Seen seen, NetworkConversationMember mine) {
        if (seen.group() != null || seen.otherId() == null || !receipts(mine.getUserId()) || !receipts(seen.otherId())) {
            return null;
        }
        return participants.findByConversationIdAndUserId(seen.conversation().getId(), seen.otherId())
            .map(NetworkConversationMember::getLastReadAt).orElse(null);
    }

    private boolean receipts(Long userId) {
        return members.findByUserId(userId).map(NetworkMember::showsReadReceipts).orElse(false);
    }

    /** Whether a message is still in its edit window. */
    boolean editable(NetworkMessage message) {
        return !message.isDeleted() && message.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().plus(EDIT_WINDOW)
            .isAfter(clock.instant());
    }

    /** Now, in the zone the rows' times are written in. */
    LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
    }

    static String excerpt(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() > PREVIEW ? flat.substring(0, PREVIEW - 1) + "…" : flat;
    }
}
