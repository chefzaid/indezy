package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkConversation;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.repository.NetworkConversationMemberRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

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

    /** The reader's place in the conversation; not found when they are not in it. */
    NetworkConversationMember mine(Long readerId, Long conversationId) {
        return participants.findByConversationIdAndUserId(conversationId, readerId)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
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

    /** Why the sender cannot write to the recipient: the recipient left, either blocks the other, or does not follow the sender. */
    Optional<NetworkMessageDtos.Closed> closed(Long senderId, Long recipientId) {
        if (members.findByUserId(recipientId).filter(NetworkMember::isActive).isEmpty()) {
            return Optional.of(NetworkMessageDtos.Closed.LEFT);
        }
        if (restrictions.blockedEitherWay(senderId).contains(recipientId)) {
            return Optional.of(NetworkMessageDtos.Closed.BLOCKED);
        }
        if (follows.findByFollowerIdAndFollowedId(recipientId, senderId).isEmpty()) {
            return Optional.of(NetworkMessageDtos.Closed.NOT_FOLLOWED);
        }
        return Optional.empty();
    }

    /** The conversation as its reader's list shows it. */
    NetworkMessageDtos.Conversation view(NetworkConversation conversation, NetworkConversationMember mine, Long otherId,
                                         NetworkDtos.MemberCard with, NetworkMessage last) {
        NetworkMessageDtos.Preview preview = last == null ? null : new NetworkMessageDtos.Preview(last.getSenderId().equals(mine.getUserId()),
            last.isDeleted() ? "" : excerpt(last.getText()), last.isDeleted(), last.getCreatedAt());
        int unread = (int) messages.unread(conversation.getId(), mine.getUserId(), mine.getLastReadAt());
        return new NetworkMessageDtos.Conversation(conversation.getId(), with, preview, unread, mine.isMuted(), mine.isArchived(),
            closed(mine.getUserId(), otherId).orElse(null), conversation.getLastMessageAt());
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
