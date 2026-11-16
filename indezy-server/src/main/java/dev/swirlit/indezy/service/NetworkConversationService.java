package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkConversation;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.repository.NetworkConversationMemberRepository;
import dev.swirlit.indezy.repository.NetworkConversationRepository;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Conversations between two members (see docs/features.md#member-network): opened only towards a member who follows the
 * sender, listed by their latest message, each with what its reader has not read, muted or archived for them alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkConversationService {

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final NetworkConversations shared;
    private final NetworkMessageLimits limits;
    private final NetworkConversationRepository conversations;
    private final NetworkConversationMemberRepository participants;
    private final NetworkMessageRepository messages;

    /**
     * The conversation with the member holding the handle: the one already open, or a new one when the member follows the
     * reader; refused (409) with the reason otherwise.
     */
    @Transactional
    public NetworkMessageDtos.Conversation open(Long userId, String handle) {
        NetworkMember viewer = network.requireMember(userId);
        NetworkMember other = follows.visible(viewer, handle);
        if (other.getUserId().equals(userId)) {
            throw new InvalidStateException("A conversation needs another member");
        }
        Optional<NetworkConversation> existing = conversations.findByPairKey(NetworkConversation.pairKey(userId, other.getUserId()));
        if (existing.isPresent()) {
            return view(existing.get(), userId);
        }
        shared.closed(userId, other.getUserId()).ifPresent(closed -> {
            throw new InvalidStateException(reason(closed));
        });
        limits.conversation(userId);
        NetworkConversation conversation = new NetworkConversation();
        conversation.setCreatedBy(userId);
        conversation.setPairKey(NetworkConversation.pairKey(userId, other.getUserId()));
        conversations.save(conversation);
        for (Long member : List.of(userId, other.getUserId())) {
            NetworkConversationMember participant = new NetworkConversationMember();
            participant.setConversationId(conversation.getId());
            participant.setUserId(member);
            participants.save(participant);
        }
        log.info("Member {} opened conversation {}", userId, conversation.getId());
        return view(conversation, userId);
    }

    /**
     * The reader's conversations, the latest message first: those archived when asked, the others otherwise; never one
     * with a member who left or with whom either blocks the other, nor one still empty that the other member opened.
     */
    @Transactional(readOnly = true)
    public List<NetworkMessageDtos.Conversation> list(Long userId, boolean archived) {
        network.requireMember(userId);
        List<NetworkConversationMember> mine = participants.findByUserId(userId).stream()
            .filter(participant -> participant.isArchived() == archived).toList();
        Map<Long, NetworkConversation> found = conversations.findAllById(mine.stream().map(NetworkConversationMember::getConversationId).toList())
            .stream().collect(Collectors.toMap(NetworkConversation::getId, Function.identity()));
        Map<Long, NetworkMessage> latest = found.isEmpty() ? Map.of() : messages.latest(found.keySet()).stream()
            .collect(Collectors.toMap(NetworkMessage::getConversationId, Function.identity()));
        Map<Long, Long> others = new HashMap<>();
        participants.findByConversationIdIn(found.keySet()).stream().filter(participant -> !participant.getUserId().equals(userId))
            .forEach(participant -> others.put(participant.getConversationId(), participant.getUserId()));
        List<NetworkMessageDtos.Conversation> shown = new ArrayList<>();
        for (NetworkConversationMember participant : mine) {
            NetworkConversation conversation = found.get(participant.getConversationId());
            Long other = others.get(participant.getConversationId());
            if (conversation == null || other == null || conversation.getLastMessageAt() == null && !conversation.getCreatedBy().equals(userId)) {
                continue;
            }
            shared.visible(userId, other).ifPresent(card -> shown.add(shared.view(conversation, participant, other, card,
                latest.get(conversation.getId()))));
        }
        shown.sort(Comparator.comparing((NetworkMessageDtos.Conversation conversation) -> Objects.requireNonNullElse(
            conversation.lastMessageAt(), LocalDateTime.MIN)).reversed());
        return shown;
    }

    /** How many conversations have messages the reader has not read, the muted aside; none away from the network. */
    @Transactional(readOnly = true)
    public NetworkMessageDtos.Unread unread(Long userId) {
        if (!network.isMember(userId)) {
            return new NetworkMessageDtos.Unread(0);
        }
        int count = 0;
        for (NetworkConversationMember participant : participants.findByUserId(userId)) {
            if (!participant.isMuted() && messages.unread(participant.getConversationId(), userId, participant.getLastReadAt()) > 0 &&
                shared.visible(userId, shared.other(participant.getConversationId(), userId)).isPresent()) {
                count++;
            }
        }
        return new NetworkMessageDtos.Unread(count);
    }

    /** Mutes or archives the conversation for the reader alone. */
    @Transactional
    public NetworkMessageDtos.Conversation settings(Long userId, Long conversationId, NetworkMessageDtos.Settings settings) {
        network.requireMember(userId);
        NetworkConversationMember mine = shared.mine(userId, conversationId);
        if (settings.muted() != null) {
            mine.setMuted(settings.muted());
        }
        if (settings.archived() != null) {
            mine.setArchived(settings.archived());
        }
        return view(conversations.findById(conversationId).orElseThrow(), userId);
    }

    /** Marks the conversation read up to now. */
    @Transactional
    public NetworkMessageDtos.Conversation read(Long userId, Long conversationId) {
        network.requireMember(userId);
        shared.mine(userId, conversationId).setLastReadAt(shared.now());
        return view(conversations.findById(conversationId).orElseThrow(), userId);
    }

    /** The messages the account sent, for its data export. */
    @Transactional(readOnly = true)
    public List<NetworkMessageDtos.Exported> export(Long userId) {
        Map<Long, String> handles = new HashMap<>();
        return messages.findBySenderIdOrderByCreatedAtAsc(userId).stream().map(message -> new NetworkMessageDtos.Exported(
            message.getConversationId(), handles.computeIfAbsent(message.getConversationId(), id -> handle(id, userId)),
            message.getText(), message.getCreatedAt(), message.isDeleted())).toList();
    }

    /** The conversation as its reader sees it in the list; not found when its other member is out of their reach. */
    NetworkMessageDtos.Conversation view(NetworkConversation conversation, Long userId) {
        NetworkConversationMember mine = shared.mine(userId, conversation.getId());
        Long other = shared.other(conversation.getId(), userId);
        NetworkDtos.MemberCard card = shared.visible(userId, other)
            .orElseThrow(() -> new ResourceNotFoundException(NetworkConversations.NOT_FOUND));
        NetworkMessage last = messages.latest(List.of(conversation.getId())).stream().findFirst().orElse(null);
        return shared.view(conversation, mine, other, card, last);
    }

    static String reason(NetworkMessageDtos.Closed closed) {
        return switch (closed) {
            case NOT_FOLLOWED -> "Messages are open only when the other member follows you";
            case BLOCKED -> "No message between members when either blocks the other";
            case LEFT -> "This member left the network";
        };
    }

    private String handle(Long conversationId, Long userId) {
        return network.handle(shared.other(conversationId, userId)).orElse(null);
    }
}
