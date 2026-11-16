package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkMessageDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkConversation;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.repository.NetworkConversationMemberRepository;
import dev.swirlit.indezy.repository.NetworkConversationRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.realtime.NetworkEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Group conversations (see docs/features.md#member-network): a member names a group and adds up to 19 members who all
 * follow them, for a collective or a mission team; its creator renames it and adds members, every member writes in it and
 * may leave it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkGroupService {

    static final int MAX_MEMBERS = 20;

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final NetworkConversationService conversationService;
    private final NetworkMessageLimits limits;
    private final NetworkConversationRepository conversations;
    private final NetworkConversationMemberRepository participants;
    private final NetworkFollowRepository followRepository;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkEvents events;

    /** Creates a group with its name and members, who all follow the reader. */
    @Transactional
    public NetworkMessageDtos.Conversation create(Long userId, NetworkMessageDtos.NewGroup request) {
        NetworkMember creator = network.requireMember(userId);
        Set<Long> added = joining(creator, request.handles(), Set.of(userId));
        limits.conversation(userId);
        NetworkConversation group = new NetworkConversation();
        group.setCreatedBy(userId);
        group.setTitle(request.title().strip());
        conversations.save(group);
        add(group, userId);
        added.forEach(member -> add(group, member));
        log.info("Member {} created group {} with {} members", userId, group.getId(), added.size() + 1);
        changed(group.getId(), List.of());
        return conversationService.view(group, userId);
    }

    /** Adds members to the reader's own group, who all follow them, up to 20 members in all. */
    @Transactional
    public NetworkMessageDtos.Conversation addMembers(Long userId, Long groupId, NetworkMessageDtos.NewMembers request) {
        NetworkMember creator = network.requireMember(userId);
        NetworkConversation group = ownGroup(userId, groupId);
        Set<Long> present = new LinkedHashSet<>(participants.findByConversationId(groupId).stream().map(NetworkConversationMember::getUserId).toList());
        Set<Long> added = joining(creator, request.handles(), present);
        if (present.size() + added.size() > MAX_MEMBERS) {
            throw new InvalidStateException("A group has 20 members at most");
        }
        added.forEach(member -> add(group, member));
        changed(groupId, List.of());
        return conversationService.view(group, userId);
    }

    /** Renames the reader's own group. */
    @Transactional
    public NetworkMessageDtos.Conversation rename(Long userId, Long groupId, NetworkMessageDtos.Rename request) {
        network.requireMember(userId);
        NetworkConversation group = ownGroup(userId, groupId);
        group.setTitle(request.title().strip());
        changed(groupId, List.of());
        return conversationService.view(group, userId);
    }

    /** Leaves a group: its messages stay for the others, the reader reads it no more. */
    @Transactional
    public void leave(Long userId, Long groupId) {
        network.requireMember(userId);
        NetworkConversation group = conversations.findById(groupId).filter(NetworkConversation::isGroup)
            .orElseThrow(() -> new ResourceNotFoundException(NetworkConversations.NOT_FOUND));
        participants.delete(participants.findByConversationIdAndUserId(group.getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException(NetworkConversations.NOT_FOUND)));
        log.info("Member {} left group {}", userId, groupId);
        changed(groupId, List.of(userId));
    }

    /** A group the reader created; not found for another's or a conversation between two, refused once they left it. */
    private NetworkConversation ownGroup(Long userId, Long groupId) {
        NetworkConversation group = conversations.findById(groupId).filter(NetworkConversation::isGroup)
            .filter(found -> participants.findByConversationIdAndUserId(found.getId(), userId).isPresent())
            .orElseThrow(() -> new ResourceNotFoundException(NetworkConversations.NOT_FOUND));
        if (!group.getCreatedBy().equals(userId)) {
            throw new InvalidStateException("Only the group's creator names it and adds members");
        }
        return group;
    }

    /**
     * The members the handles name, but those already in: each in the network, following the creator, neither blocking
     * the other; refused (409) with the first handle that is not.
     */
    private Set<Long> joining(NetworkMember creator, List<String> handles, Set<Long> present) {
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(creator.getUserId()));
        Set<Long> joining = new LinkedHashSet<>();
        for (String handle : handles) {
            NetworkMember member = follows.visible(creator, handle.strip().toLowerCase(Locale.ROOT));
            if (present.contains(member.getUserId())) {
                continue;
            }
            if (blocked.contains(member.getUserId()) ||
                followRepository.findByFollowerIdAndFollowedId(member.getUserId(), creator.getUserId()).isEmpty()) {
                throw new InvalidStateException("Every member of a group follows its creator: @" + member.getHandle() + " does not");
            }
            joining.add(member.getUserId());
        }
        if (present.size() + joining.size() > MAX_MEMBERS) {
            throw new InvalidStateException("A group has 20 members at most");
        }
        return joining;
    }

    /** Tells the group's members, and those who just left it, that it changed. */
    private void changed(Long groupId, List<Long> left) {
        Set<Long> told = new LinkedHashSet<>(left);
        participants.findByConversationId(groupId).forEach(participant -> told.add(participant.getUserId()));
        events.message(told, groupId);
    }

    private void add(NetworkConversation group, Long userId) {
        NetworkConversationMember participant = new NetworkConversationMember();
        participant.setConversationId(group.getId());
        participant.setUserId(userId);
        participants.save(participant);
    }
}
