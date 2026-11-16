package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkConversation;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.model.NetworkMessageReaction;
import dev.swirlit.indezy.model.enums.NetworkReaction;
import dev.swirlit.indezy.repository.NetworkConversationMemberRepository;
import dev.swirlit.indezy.repository.NetworkConversationRepository;
import dev.swirlit.indezy.repository.NetworkMessageReactionRepository;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The messages of a conversation (see docs/features.md#member-network): read a page at a time, sent while the other
 * member follows the sender, replying to an earlier one, edited or deleted for both sides by their sender within 15
 * minutes, and reacted to with one emoji each.
 */
@Service
@RequiredArgsConstructor
public class NetworkMessageService {

    static final int PAGE_SIZE = 30;
    static final String MESSAGE_NOT_FOUND = "No such message";

    private final NetworkService network;
    private final NetworkConversations shared;
    private final NetworkConversationService conversationService;
    private final NetworkMessageLimits limits;
    private final NetworkConversationRepository conversations;
    private final NetworkConversationMemberRepository participants;
    private final NetworkMessageRepository messages;
    private final NetworkMessageReactionRepository reactions;
    private final Clock clock;

    /** The conversation with a page of its messages before the given one (the latest with none), the oldest first. */
    @Transactional(readOnly = true)
    public NetworkMessageDtos.Thread thread(Long userId, Long conversationId, Long before) {
        network.requireMember(userId);
        NetworkMessageDtos.Conversation conversation = conversationService.view(conversation(conversationId), userId);
        List<NetworkMessage> found = new ArrayList<>(messages.page(conversationId, before, PageRequest.of(0, PAGE_SIZE + 1)));
        boolean more = found.size() > PAGE_SIZE;
        List<NetworkMessage> shown = new ArrayList<>(found.subList(0, Math.min(found.size(), PAGE_SIZE)));
        Collections.reverse(shown);
        return new NetworkMessageDtos.Thread(conversation, views(shown, userId), more);
    }

    /** Sends a message, refused (409) with the reason when the other member no longer follows the sender. */
    @Transactional
    public NetworkMessageDtos.Message send(Long userId, Long conversationId, NetworkMessageDtos.NewMessage request) {
        network.requireMember(userId);
        NetworkConversation conversation = conversation(conversationId);
        conversationService.view(conversation, userId);
        NetworkConversationMember mine = shared.mine(userId, conversationId);
        Long other = shared.other(conversationId, userId);
        shared.closed(userId, other).ifPresent(closed -> {
            throw new InvalidStateException(NetworkConversationService.reason(closed));
        });
        limits.message(userId);
        if (request.replyTo() != null && messages.findById(request.replyTo())
            .filter(replied -> replied.getConversationId().equals(conversationId)).isEmpty()) {
            throw new IllegalArgumentException("A reply answers a message of the same conversation");
        }
        NetworkMessage message = new NetworkMessage();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setText(request.text().strip());
        message.setReplyToId(request.replyTo());
        messages.save(message);
        conversation.setLastMessageAt(message.getCreatedAt() == null ? shared.now() : message.getCreatedAt());
        mine.setLastReadAt(conversation.getLastMessageAt());
        // A new message brings an archived conversation back to the list.
        participants.findByConversationId(conversationId).forEach(participant -> participant.setArchived(null));
        return views(List.of(message), userId).getFirst();
    }

    /** Changes the text of the reader's own message within 15 minutes of sending it. */
    @Transactional
    public NetworkMessageDtos.Message edit(Long userId, Long messageId, NetworkMessageDtos.EditedMessage request) {
        NetworkMessage message = ownInWindow(userId, messageId, "edited");
        message.setText(request.text().strip());
        message.setEditedAt(clock.instant());
        return views(List.of(message), userId).getFirst();
    }

    /** Deletes the reader's own message for both sides within 15 minutes of sending it; "Message deleted" stays. */
    @Transactional
    public NetworkMessageDtos.Message delete(Long userId, Long messageId) {
        NetworkMessage message = ownInWindow(userId, messageId, "deleted");
        message.setText("");
        message.setDeletedAt(clock.instant());
        reactions.deleteAll(reactions.findByMessageIdIn(List.of(messageId)));
        return views(List.of(message), userId).getFirst();
    }

    /** Reacts to a message of the reader's conversation, or takes the reaction back (null). */
    @Transactional
    public NetworkMessageDtos.Message react(Long userId, Long messageId, NetworkReaction reaction) {
        network.requireMember(userId);
        NetworkMessage message = readable(userId, messageId);
        if (message.isDeleted()) {
            throw new InvalidStateException("A deleted message takes no reaction");
        }
        var existing = reactions.findByMessageIdAndUserId(messageId, userId);
        if (reaction == null) {
            existing.ifPresent(reactions::delete);
        } else {
            NetworkMessageReaction given = existing.orElseGet(NetworkMessageReaction::new);
            given.setMessageId(messageId);
            given.setUserId(userId);
            given.setReaction(reaction);
            reactions.save(given);
        }
        return views(List.of(message), userId).getFirst();
    }

    private NetworkConversation conversation(Long conversationId) {
        return conversations.findById(conversationId).orElseThrow(() -> new ResourceNotFoundException(NetworkConversations.NOT_FOUND));
    }

    /** A message of a conversation the reader is in, with its other member still in reach. */
    private NetworkMessage readable(Long userId, Long messageId) {
        NetworkMessage message = messages.findById(messageId).orElseThrow(() -> new ResourceNotFoundException(MESSAGE_NOT_FOUND));
        try {
            conversationService.view(conversation(message.getConversationId()), userId);
        } catch (ResourceNotFoundException hidden) {
            throw new ResourceNotFoundException(MESSAGE_NOT_FOUND);
        }
        return message;
    }

    private NetworkMessage ownInWindow(Long userId, Long messageId, String change) {
        network.requireMember(userId);
        NetworkMessage message = readable(userId, messageId);
        if (!message.getSenderId().equals(userId)) {
            throw new ResourceNotFoundException(MESSAGE_NOT_FOUND);
        }
        if (!shared.editable(message)) {
            throw new InvalidStateException("A message can only be " + change + " in the 15 minutes after it is sent");
        }
        return message;
    }

    private List<NetworkMessageDtos.Message> views(List<NetworkMessage> shown, Long readerId) {
        List<Long> ids = shown.stream().map(NetworkMessage::getId).toList();
        Map<Long, List<NetworkMessageReaction>> reacted = ids.isEmpty() ? Map.of() : reactions.findByMessageIdIn(ids).stream()
            .collect(Collectors.groupingBy(NetworkMessageReaction::getMessageId));
        List<Long> replied = shown.stream().map(NetworkMessage::getReplyToId).filter(id -> id != null).distinct().toList();
        Map<Long, NetworkMessage> replies = replied.isEmpty() ? Map.of() : messages.findAllById(replied).stream()
            .collect(Collectors.toMap(NetworkMessage::getId, Function.identity()));
        return shown.stream().map(message -> {
            List<NetworkMessageReaction> given = reacted.getOrDefault(message.getId(), List.of());
            NetworkMessage original = message.getReplyToId() == null ? null : replies.get(message.getReplyToId());
            NetworkMessageDtos.Reply reply = original == null ? null : new NetworkMessageDtos.Reply(original.getId(),
                original.getSenderId().equals(readerId), original.isDeleted() ? "" : NetworkConversations.excerpt(original.getText()),
                original.isDeleted());
            boolean mine = message.getSenderId().equals(readerId);
            return new NetworkMessageDtos.Message(message.getId(), mine, message.getText(), message.getCreatedAt(),
                message.getEditedAt() != null, message.isDeleted(), mine && shared.editable(message), reply, counts(given),
                given.stream().filter(reaction -> reaction.getUserId().equals(readerId)).map(NetworkMessageReaction::getReaction)
                    .findFirst().orElse(null));
        }).toList();
    }

    private static List<NetworkDtos.ReactionCount> counts(List<NetworkMessageReaction> given) {
        Map<NetworkReaction, Long> counted = given.stream().collect(Collectors.groupingBy(NetworkMessageReaction::getReaction,
            () -> new EnumMap<>(NetworkReaction.class), Collectors.counting()));
        return counted.entrySet().stream().sorted(Map.Entry.<NetworkReaction, Long>comparingByValue().reversed())
            .map(entry -> new NetworkDtos.ReactionCount(entry.getKey(), entry.getValue().intValue())).toList();
    }
}
