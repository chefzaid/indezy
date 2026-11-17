package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkConversation;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.pdf.ConversationPdfRenderer;
import dev.swirlit.indezy.repository.NetworkConversationMemberRepository;
import dev.swirlit.indezy.repository.NetworkConversationRepository;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import dev.swirlit.indezy.realtime.NetworkEvents;
import dev.swirlit.indezy.scraping.FrenchDates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
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
    private final ConversationPdfRenderer pdf;
    private final Clock clock;
    private final NetworkEvents events;

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
        List<NetworkMessageDtos.Conversation> shown = new ArrayList<>();
        for (NetworkConversationMember participant : mine) {
            NetworkConversation conversation = found.get(participant.getConversationId());
            if (conversation == null || conversation.getLastMessageAt() == null && !conversation.getCreatedBy().equals(userId)) {
                continue;
            }
            shared.seen(userId, conversation).ifPresent(seen -> shown.add(shared.view(seen, participant, latest.get(conversation.getId()))));
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
                conversations.findById(participant.getConversationId()).flatMap(conversation -> shared.seen(userId, conversation)).isPresent()) {
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
        events.read(participants.findByConversationId(conversationId).stream().map(NetworkConversationMember::getUserId).toList(),
            conversationId);
        return view(conversations.findById(conversationId).orElseThrow(), userId);
    }

    /** The whole conversation as a PDF for its reader, in the language asked. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile pdf(Long userId, Long conversationId, DocumentLanguage language) {
        NetworkMember reader = network.requireMember(userId);
        NetworkConversation conversation = conversations.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException(NetworkConversations.NOT_FOUND));
        NetworkConversations.Seen seen = seen(conversation, userId);
        List<NetworkMessage> all = messages.findByConversationIdOrderByIdAsc(conversationId);
        Map<Long, NetworkMessage> byId = all.stream().collect(Collectors.toMap(NetworkMessage::getId, Function.identity()));
        Map<Long, String> senders = new HashMap<>();
        List<ConversationPdfRenderer.Line> lines = all.stream().map(message -> {
            NetworkMessage replied = message.getReplyToId() == null ? null : byId.get(message.getReplyToId());
            String reply = replied == null ? null : replied.isDeleted() ? "…" : NetworkConversations.excerpt(replied.getText());
            String sender = message.getSenderId().equals(userId) ? null : senders.computeIfAbsent(message.getSenderId(), id -> shared.card(id)
                .map(card -> (card.firstName() + " " + card.lastName()).strip()).orElse("—"));
            return new ConversationPdfRenderer.Line(sender, message.getCreatedAt(), message.getText(), message.getEditedAt() != null,
                message.isDeleted(), reply);
        }).toList();
        String readerName = network.liveCard(reader).map(card -> (card.firstName() + " " + card.lastName()).strip()).orElse(null);
        // The day as members read it, in Paris, not the server's UTC.
        LocalDate today = LocalDate.now(clock.withZone(FrenchDates.PARIS));
        byte[] content = pdf.render(new ConversationPdfRenderer.Conversation(readerName, seen.label(), seen.handle(), lines), language, today);
        String name = seen.handle() != null ? seen.handle() : (seen.group() != null ? "group-" : "former-member-") + conversationId;
        return new ActivityReportService.PdfFile("conversation-" + name + "-" + today + ".pdf", content);
    }

    /** The messages the account sent, for its data export. */
    @Transactional(readOnly = true)
    public List<NetworkMessageDtos.Exported> export(Long userId) {
        Map<Long, String> handles = new HashMap<>();
        return messages.findBySenderIdOrderByCreatedAtAsc(userId).stream().map(message -> new NetworkMessageDtos.Exported(
            message.getConversationId(), handles.computeIfAbsent(message.getConversationId(), id -> handle(id, userId)),
            message.getText(), message.getCreatedAt(), message.isDeleted())).toList();
    }

    /** The conversation as its reader sees it in the list; not found when it is out of their reach. */
    NetworkMessageDtos.Conversation view(NetworkConversation conversation, Long userId) {
        NetworkConversationMember mine = shared.mine(userId, conversation.getId());
        NetworkMessage last = messages.latest(List.of(conversation.getId())).stream().findFirst().orElse(null);
        return shared.view(seen(conversation, userId), mine, last);
    }

    /** How the conversation reads for the reader; not found when it is out of their reach. */
    NetworkConversations.Seen seen(NetworkConversation conversation, Long userId) {
        return shared.seen(userId, conversation).orElseThrow(() -> new ResourceNotFoundException(NetworkConversations.NOT_FOUND));
    }

    static String reason(NetworkMessageDtos.Closed closed) {
        return switch (closed) {
            case NOT_FOLLOWED -> "Messages are open only when the other member follows you";
            case BLOCKED -> "No message between members when either blocks the other";
            case LEFT -> "This member left the network";
        };
    }

    /** Who a message went to, for the export: the other member's handle, or the group's name. */
    private String handle(Long conversationId, Long userId) {
        NetworkConversation conversation = conversations.findById(conversationId).orElse(null);
        if (conversation == null || conversation.isGroup()) {
            return conversation == null ? null : conversation.getTitle();
        }
        return network.handle(shared.other(conversationId, userId)).orElse(null);
    }
}
