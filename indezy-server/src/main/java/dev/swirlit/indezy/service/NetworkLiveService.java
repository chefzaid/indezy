package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.realtime.NetworkEventHub;
import dev.swirlit.indezy.realtime.NetworkPresence;
import dev.swirlit.indezy.realtime.NetworkEvents;
import dev.swirlit.indezy.repository.NetworkConversationMemberRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

/**
 * The network as it happens (see docs/features.md#member-network): a member's stream of what changed, who is typing in a
 * conversation, and whether the member shows and sees read receipts and who is typing.
 */
@Service
@RequiredArgsConstructor
public class NetworkLiveService {

    private final NetworkService network;
    private final NetworkConversations shared;
    private final NetworkConversationMemberRepository participants;
    private final NetworkMemberRepository members;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkEventHub hub;
    private final NetworkPresence presence;
    private final NetworkEvents events;

    /** Opens the member's stream. */
    @Transactional(readOnly = true)
    public SseEmitter stream(Long userId) {
        network.requireMember(userId);
        SseEmitter stream = hub.open(userId);
        presence.seen(userId);
        return stream;
    }

    /**
     * Tells the conversation's other members, those in reach who see who is typing, that the reader is typing; nothing
     * when the reader does not show it.
     */
    @Transactional(readOnly = true)
    public void typing(Long userId, Long conversationId) {
        NetworkMember reader = network.requireMember(userId);
        shared.mine(userId, conversationId);
        if (!reader.showsTyping()) {
            return;
        }
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        List<Long> to = participants.findByConversationId(conversationId).stream().map(NetworkConversationMember::getUserId)
            .filter(other -> !other.equals(userId) && !blocked.contains(other))
            .filter(other -> members.findByUserId(other).filter(member -> member.isActive() && member.showsTyping()).isPresent())
            .toList();
        String name = shared.card(userId).map(card -> card.firstName()).orElse("");
        events.typing(to, conversationId, name);
    }

    @Transactional(readOnly = true)
    public NetworkMessageDtos.Messaging messaging(Long userId) {
        return view(network.requireMember(userId));
    }

    @Transactional
    public NetworkMessageDtos.Messaging saveMessaging(Long userId, NetworkMessageDtos.Messaging settings) {
        NetworkMember member = network.requireMember(userId);
        if (settings.readReceipts() != null) {
            member.setReadReceipts(settings.readReceipts());
        }
        if (settings.typing() != null) {
            member.setTypingShown(settings.typing());
        }
        if (settings.presence() != null) {
            member.setPresenceShown(settings.presence());
        }
        if (settings.requests() != null) {
            member.setMessageRequests(settings.requests());
        }
        return view(member);
    }

    private static NetworkMessageDtos.Messaging view(NetworkMember member) {
        return new NetworkMessageDtos.Messaging(member.showsReadReceipts(), member.showsTyping(), member.showsPresence(),
            member.acceptsMessageRequests());
    }
}
