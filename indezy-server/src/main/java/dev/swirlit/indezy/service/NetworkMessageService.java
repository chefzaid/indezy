package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.NetworkFileDtos;
import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkConversation;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.model.NetworkMessageReaction;
import dev.swirlit.indezy.model.NetworkOpportunityCard;
import dev.swirlit.indezy.model.enums.NetworkReaction;
import dev.swirlit.indezy.realtime.NetworkEvents;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    static final int SEARCHED = 20;

    private final NetworkService network;
    private final NetworkConversations shared;
    private final NetworkConversationService conversationService;
    private final NetworkMessageLimits limits;
    private final NetworkConversationRepository conversations;
    private final NetworkConversationMemberRepository participants;
    private final NetworkMessageRepository messages;
    private final NetworkMessageReactionRepository reactions;
    private final NetworkPipelineService pipeline;
    private final NetworkLinkPreviews previews;
    private final NetworkEvents events;
    private final NetworkFileService files;
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

    /**
     * Sends a message, refused (409) with the reason when the other member no longer follows the sender, or a message
     * request waits for its answer after its one message.
     */
    @Transactional
    public NetworkMessageDtos.Message send(Long userId, Long conversationId, NetworkMessageDtos.NewMessage request) {
        network.requireMember(userId);
        NetworkConversation conversation = conversation(conversationId);
        NetworkConversations.Seen seen = conversationService.seen(conversation, userId);
        NetworkConversationMember mine = shared.mine(userId, conversationId);
        if (!conversation.isGroup()) {
            shared.closed(userId, seen.otherId(), conversation).ifPresent(closed -> {
                throw new InvalidStateException(NetworkConversationService.reason(closed));
            });
        }
        limits.message(userId);
        String text = request.text() == null ? "" : request.text().strip();
        boolean carries = request.files() != null && !request.files().isEmpty();
        if (text.isEmpty() && request.opportunityId() == null && !carries) {
            throw new IllegalArgumentException("A message needs a text, a file or an opportunity");
        }
        if (request.replyTo() != null && messages.findById(request.replyTo())
            .filter(replied -> replied.getConversationId().equals(conversationId)).isEmpty()) {
            throw new IllegalArgumentException("A reply answers a message of the same conversation");
        }
        NetworkMessage message = new NetworkMessage();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setText(text);
        message.setReplyToId(request.replyTo());
        if (request.opportunityId() != null) {
            message.setCard(pipeline.card(userId, request.opportunityId()));
        }
        messages.save(message);
        files.attachToMessage(userId, request.files(), message.getId());
        previews.refresh(message);
        conversation.setLastMessageAt(message.getCreatedAt() == null ? shared.now() : message.getCreatedAt());
        mine.setLastReadAt(conversation.getLastMessageAt());
        // A new message brings an archived conversation back to the list.
        List<NetworkConversationMember> everyone = participants.findByConversationId(conversationId);
        everyone.forEach(participant -> participant.setArchived(null));
        events.message(everyone.stream().map(NetworkConversationMember::getUserId).toList(), conversationId);
        return views(List.of(message), userId).getFirst();
    }

    /** Changes the text of the reader's own message within 15 minutes of sending it. */
    @Transactional
    public NetworkMessageDtos.Message edit(Long userId, Long messageId, NetworkMessageDtos.EditedMessage request) {
        NetworkMessage message = ownInWindow(userId, messageId, "edited");
        message.setText(request.text().strip());
        message.setEditedAt(clock.instant());
        previews.refresh(message);
        changed(message);
        return views(List.of(message), userId).getFirst();
    }

    /** Deletes the reader's own message for both sides within 15 minutes of sending it; "Message deleted" stays. */
    @Transactional
    public NetworkMessageDtos.Message delete(Long userId, Long messageId) {
        NetworkMessage message = ownInWindow(userId, messageId, "deleted");
        message.setText("");
        message.setCard(null);
        message.setPreview(null);
        message.setDeletedAt(clock.instant());
        reactions.deleteAll(reactions.findByMessageIdIn(List.of(messageId)));
        files.deleteForMessage(messageId);
        changed(message);
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
        changed(message);
        return views(List.of(message), userId).getFirst();
    }

    private NetworkConversation conversation(Long conversationId) {
        return conversations.findById(conversationId).orElseThrow(() -> new ResourceNotFoundException(NetworkConversations.NOT_FOUND));
    }

    /** Adds the opportunity a message of the reader's conversation shares to their pipeline, once; answers the opportunity. */
    @Transactional
    public ProjectDto take(Long userId, Long messageId) {
        network.requireMember(userId);
        NetworkMessage message = readable(userId, messageId);
        if (message.getCard() == null || message.isDeleted()) {
            throw new InvalidStateException("This message shares no opportunity");
        }
        if (message.getSenderId().equals(userId)) {
            throw new InvalidStateException("This opportunity is yours");
        }
        return pipeline.takeCard(userId, message.getCard(), message.getSenderId());
    }

    /** Empties a message the moderators removed, for both sides. */
    @Transactional
    public void removeByModerators(Long messageId) {
        messages.findById(messageId).filter(message -> !message.isDeleted()).ifPresent(message -> {
            message.setText("");
            message.setCard(null);
            message.setPreview(null);
            message.setDeletedAt(clock.instant());
            reactions.deleteAll(reactions.findByMessageIdIn(List.of(messageId)));
            files.deleteForMessage(messageId);
            changed(message);
        });
    }

    /**
     * The messages of the reader's conversations whose text holds what was typed (case aside), the latest first, 20 at
     * most, but those of conversations out of their reach.
     */
    @Transactional(readOnly = true)
    public List<NetworkMessageDtos.Found> search(Long userId, String typed) {
        network.requireMember(userId);
        String words = typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT);
        if (words.length() < 2) {
            return List.of();
        }
        Map<Long, NetworkConversations.Seen> reachable = new HashMap<>();
        List<Long> mine = participants.findByUserId(userId).stream().map(NetworkConversationMember::getConversationId).toList();
        conversations.findAllById(mine).forEach(conversation -> shared.seen(userId, conversation)
            .ifPresent(seen -> reachable.put(conversation.getId(), seen)));
        if (reachable.isEmpty()) {
            return List.of();
        }
        String pattern = "%" + words.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Map<Long, Optional<NetworkDtos.MemberCard>> senders = new HashMap<>();
        return messages.search(reachable.keySet(), pattern, PageRequest.of(0, SEARCHED)).stream().map(message -> {
            NetworkConversations.Seen seen = reachable.get(message.getConversationId());
            boolean own = message.getSenderId().equals(userId);
            NetworkDtos.MemberCard shown = seen.with() != null ? seen.with() : own ? null :
                senders.computeIfAbsent(message.getSenderId(), shared::card).orElse(null);
            return new NetworkMessageDtos.Found(message.getConversationId(), seen.label(), shown, message.getId(), own,
                NetworkConversations.excerpt(message.getText()), message.getCreatedAt());
        }).toList();
    }

    /**
     * The messages of the reader's conversations holding every word (as the global search reads them), the latest first,
     * {@code max} at most: found by the first word, then kept when the others are there too.
     */
    @Transactional(readOnly = true)
    public List<NetworkMessageDtos.Found> searchAll(Long userId, List<String> words, int max) {
        if (words.isEmpty()) {
            return List.of();
        }
        List<NetworkMessageDtos.Found> found = search(userId, words.getFirst());
        return found.stream().filter(hit -> messages.findById(hit.messageId())
            .filter(message -> GlobalSearchService.containsAll(message.getText(), words)).isPresent()).limit(max).toList();
    }

    /** A message of a conversation the reader is in, with its other member still in reach. */
    NetworkMessage readable(Long userId, Long messageId) {
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

    /** Tells the conversation's members that one of its messages changed. */
    private void changed(NetworkMessage message) {
        events.message(participants.findByConversationId(message.getConversationId()).stream().map(NetworkConversationMember::getUserId)
            .toList(), message.getConversationId());
    }

    private List<NetworkMessageDtos.Message> views(List<NetworkMessage> shown, Long readerId) {
        Map<Long, Optional<NetworkDtos.MemberCard>> senders = new HashMap<>();
        List<Long> ids = shown.stream().map(NetworkMessage::getId).toList();
        Map<Long, List<NetworkMessageReaction>> reacted = ids.isEmpty() ? Map.of() : reactions.findByMessageIdIn(ids).stream()
            .collect(Collectors.groupingBy(NetworkMessageReaction::getMessageId));
        List<Long> replied = shown.stream().map(NetworkMessage::getReplyToId).filter(id -> id != null).distinct().toList();
        Map<Long, NetworkMessage> replies = replied.isEmpty() ? Map.of() : messages.findAllById(replied).stream()
            .collect(Collectors.toMap(NetworkMessage::getId, Function.identity()));
        Map<Long, List<NetworkFileDtos.File>> carried = files.forMessages(ids);
        return shown.stream().map(message -> {
            List<NetworkMessageReaction> given = reacted.getOrDefault(message.getId(), List.of());
            NetworkMessage original = message.getReplyToId() == null ? null : replies.get(message.getReplyToId());
            NetworkMessageDtos.Reply reply = original == null ? null : new NetworkMessageDtos.Reply(original.getId(),
                original.getSenderId().equals(readerId), original.isDeleted() ? "" : NetworkConversations.excerpt(original.getText()),
                original.isDeleted());
            boolean mine = message.getSenderId().equals(readerId);
            NetworkDtos.MemberCard from = mine ? null : senders.computeIfAbsent(message.getSenderId(), shared::card).orElse(null);
            return new NetworkMessageDtos.Message(message.getId(), mine, from, message.getText(), message.getCreatedAt(),
                message.getEditedAt() != null, message.isDeleted(), mine && shared.editable(message), reply, counts(given),
                given.stream().filter(reaction -> reaction.getUserId().equals(readerId)).map(NetworkMessageReaction::getReaction)
                    .findFirst().orElse(null), card(message, mine),
                NetworkLinkPreviews.view(message.getPreview()), carried.getOrDefault(message.getId(), List.of()));
        }).toList();
    }

    /** The card a message shares, its pipeline opportunity shown to its recipient alone; none for a plain or deleted message. */
    private static NetworkMessageDtos.Card card(NetworkMessage message, boolean mine) {
        NetworkOpportunityCard card = message.getCard();
        if (card == null || message.isDeleted() || card.getRole() == null) {
            return null;
        }
        List<String> stack = card.getTechStack() == null ? List.of() : Arrays.stream(card.getTechStack().split(","))
            .map(String::strip).filter(skill -> !skill.isEmpty()).toList();
        return new NetworkMessageDtos.Card(card.getRole(), card.getDailyRate(), card.getCity(), card.getStartDate(), card.getRemoteDaysPerMonth(),
            stack, mine ? null : card.getTakenProjectId());
    }

    private static List<NetworkDtos.ReactionCount> counts(List<NetworkMessageReaction> given) {
        Map<NetworkReaction, Long> counted = given.stream().collect(Collectors.groupingBy(NetworkMessageReaction::getReaction,
            () -> new EnumMap<>(NetworkReaction.class), Collectors.counting()));
        return counted.entrySet().stream().sorted(Map.Entry.<NetworkReaction, Long>comparingByValue().reversed())
            .map(entry -> new NetworkDtos.ReactionCount(entry.getKey(), entry.getValue().intValue())).toList();
    }
}
