package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkFollow;
import dev.swirlit.indezy.model.NetworkFollowRequest;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkFollowRequestRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Private cards (see docs/features.md#member-network): a member who turns theirs private approves who follows them, and
 * only their followers read their posts; asking to follow them sends a request they approve or decline. Turning the card
 * public again approves every request waiting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkFollowRequestService {

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkFollowRepository follows;
    private final NetworkFollowRequestRepository requests;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkNotificationService notices;

    /** The members asking to follow the member, the oldest first, but those blocked either way or away. */
    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> requests(Long userId) {
        network.requireMember(userId);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        return requests.findByMemberIdOrderByCreatedAtAscIdAsc(userId).stream()
            .filter(request -> !blocked.contains(request.getRequesterId()))
            .map(request -> members.findByUserId(request.getRequesterId()).filter(NetworkMember::isActive).flatMap(network::liveCard))
            .flatMap(Optional::stream).toList();
    }

    /** Approves a member's request, who then follows and is told, or declines it, untold; answers the requests left. */
    @Transactional
    public List<NetworkDtos.MemberCard> answer(Long userId, String handle, boolean approve) {
        network.requireMember(userId);
        NetworkMember requester = network.activeMember(handle);
        NetworkFollowRequest request = requests.findByRequesterIdAndMemberId(requester.getUserId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("No request from this member"));
        if (approve) {
            approve(request);
        }
        requests.delete(request);
        return requests(userId);
    }

    /** Turns the member's card private, or public again, approving every request waiting. */
    @Transactional
    public NetworkDtos.Membership setPrivate(Long userId, boolean privateCard) {
        NetworkMember member = network.requireMember(userId);
        member.setPrivateCard(privateCard);
        if (!privateCard) {
            List<NetworkFollowRequest> waiting = requests.findByMemberIdOrderByCreatedAtAscIdAsc(userId);
            waiting.forEach(this::approve);
            requests.deleteAll(waiting);
        }
        log.info("Member {} turned their card {}", userId, privateCard ? "private" : "public");
        return network.membership(userId);
    }

    private void approve(NetworkFollowRequest request) {
        if (follows.findByFollowerIdAndFollowedId(request.getRequesterId(), request.getMemberId()).isEmpty()) {
            NetworkFollow follow = new NetworkFollow();
            follow.setFollowerId(request.getRequesterId());
            follow.setFollowedId(request.getMemberId());
            follows.save(follow);
            notices.notify(request.getRequesterId(), request.getMemberId(), NetworkNotificationType.FOLLOW_ACCEPTED, null);
        }
    }
}
