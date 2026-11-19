package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkFollow;
import dev.swirlit.indezy.model.NetworkFollowRequest;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkRestriction;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkFollowRequestRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Members following members (see docs/features.md#member-network): a member's page with its counts and the skills it
 * shares with the viewer, following and unfollowing, the lists of followers and following, removing a follower, and
 * muting or blocking a member.
 * Members who left the network leave every count and list while they are away.
 */
@Service
@RequiredArgsConstructor
public class NetworkFollowService {

    /** How many members followed by both a mutual follow names. */
    static final int MUTUAL_SHOWN = 3;

    private final NetworkService network;
    private final NetworkFollowRepository follows;
    private final NetworkFollowRequestRepository requests;
    private final NetworkMemberRepository members;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkLimits limits;
    private final NetworkNotificationService notices;

    @Transactional(readOnly = true)
    public NetworkDtos.MemberPage page(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        return page(viewer, visible(viewer, handle));
    }

    @Transactional
    public NetworkDtos.MemberPage follow(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        NetworkMember member = visible(viewer, handle);
        if (viewer.getUserId().equals(member.getUserId())) {
            throw new IllegalArgumentException("A member cannot follow themselves");
        }
        if (restricted(viewerId, member.getUserId(), NetworkRestrictionKind.BLOCK)) {
            throw new InvalidStateException("Unblock this member to follow them");
        }
        if (member.isPrivate() && follows.findByFollowerIdAndFollowedId(viewerId, member.getUserId()).isEmpty()) {
            if (requests.findByRequesterIdAndMemberId(viewerId, member.getUserId()).isEmpty()) {
                limits.follow(viewerId);
                NetworkFollowRequest request = new NetworkFollowRequest();
                request.setRequesterId(viewerId);
                request.setMemberId(member.getUserId());
                requests.save(request);
                notices.notify(member.getUserId(), viewerId, NetworkNotificationType.FOLLOW_REQUEST, null);
            }
        } else if (follows.findByFollowerIdAndFollowedId(viewerId, member.getUserId()).isEmpty()) {
            limits.follow(viewerId);
            NetworkFollow follow = new NetworkFollow();
            follow.setFollowerId(viewerId);
            follow.setFollowedId(member.getUserId());
            follows.save(follow);
            notices.notify(member.getUserId(), viewerId, NetworkNotificationType.FOLLOW, null);
        }
        return page(viewer, member);
    }

    @Transactional
    public NetworkDtos.MemberPage unfollow(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        NetworkMember member = network.activeMember(handle);
        follows.findByFollowerIdAndFollowedId(viewerId, member.getUserId()).ifPresent(follows::delete);
        requests.findByRequesterIdAndMemberId(viewerId, member.getUserId()).ifPresent(requests::delete);
        return page(viewer, member);
    }

    /** Stops a member following the viewer, without telling them. */
    @Transactional
    public void removeFollower(Long viewerId, String handle) {
        network.requireMember(viewerId);
        NetworkMember follower = network.activeMember(handle);
        follows.findByFollowerIdAndFollowedId(follower.getUserId(), viewerId).ifPresent(follows::delete);
    }

    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> followers(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        return seen(viewerId, follows.followers(visible(viewer, handle).getUserId()));
    }

    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> following(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        return seen(viewerId, follows.following(visible(viewer, handle).getUserId()));
    }

    /**
     * The members the viewer follows who follow the member too ("Followed by Léo and 2 others you follow"): how many, and
     * the first three, the latest followers first; none on the viewer's own page.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.MutualFollows mutual(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        NetworkMember member = visible(viewer, handle);
        if (member.getUserId().equals(viewerId)) {
            return new NetworkDtos.MutualFollows(0, List.of());
        }
        Set<Long> followed = follows.following(viewerId).stream().map(NetworkMember::getUserId).collect(Collectors.toSet());
        List<NetworkDtos.MemberCard> both = seen(viewerId, follows.followers(member.getUserId()).stream()
            .filter(follower -> followed.contains(follower.getUserId())).toList());
        return new NetworkDtos.MutualFollows(both.size(), both.stream().limit(MUTUAL_SHOWN).toList());
    }

    /** Mutes a member, or no longer: their posts are hidden from the viewer, who still follows them. */
    @Transactional
    public NetworkDtos.MemberPage mute(Long viewerId, String handle, boolean muted) {
        return restrict(viewerId, handle, NetworkRestrictionKind.MUTE, muted);
    }

    /**
     * Blocks a member, or no longer: neither follows the other any more, nor can, and the blocked member no longer finds
     * the viewer's page. The blocked member is not told.
     */
    @Transactional
    public NetworkDtos.MemberPage block(Long viewerId, String handle, boolean blocked) {
        NetworkDtos.MemberPage page = restrict(viewerId, handle, NetworkRestrictionKind.BLOCK, blocked);
        if (blocked) {
            Long memberId = network.activeMember(handle).getUserId();
            follows.findByFollowerIdAndFollowedId(viewerId, memberId).ifPresent(follows::delete);
            follows.findByFollowerIdAndFollowedId(memberId, viewerId).ifPresent(follows::delete);
            requests.findByRequesterIdAndMemberId(viewerId, memberId).ifPresent(requests::delete);
            requests.findByRequesterIdAndMemberId(memberId, viewerId).ifPresent(requests::delete);
            return page(network.requireMember(viewerId), network.activeMember(handle));
        }
        return page;
    }

    /** The members the viewer mutes and blocks. */
    @Transactional(readOnly = true)
    public NetworkDtos.Restrictions restrictions(Long viewerId) {
        network.requireMember(viewerId);
        return new NetworkDtos.Restrictions(
            restrictions.targets(viewerId, NetworkRestrictionKind.MUTE).stream().map(network::liveCard).flatMap(Optional::stream).toList(),
            restrictions.targets(viewerId, NetworkRestrictionKind.BLOCK).stream().map(network::liveCard).flatMap(Optional::stream).toList());
    }

    private NetworkDtos.MemberPage restrict(Long viewerId, String handle, NetworkRestrictionKind kind, boolean on) {
        NetworkMember viewer = network.requireMember(viewerId);
        NetworkMember member = visible(viewer, handle);
        if (viewer.getUserId().equals(member.getUserId())) {
            throw new IllegalArgumentException("A member cannot mute or block themselves");
        }
        Optional<NetworkRestriction> existing = restrictions.findByOwnerIdAndTargetIdAndKind(viewerId, member.getUserId(), kind);
        if (on && existing.isEmpty()) {
            NetworkRestriction restriction = new NetworkRestriction();
            restriction.setOwnerId(viewerId);
            restriction.setTargetId(member.getUserId());
            restriction.setKind(kind);
            restrictions.save(restriction);
        } else if (!on) {
            existing.ifPresent(restrictions::delete);
        }
        return page(viewer, member);
    }

    /** The member holding the handle, unless they block the viewer: then, as for an unknown handle, none is found. */
    NetworkMember visible(NetworkMember viewer, String handle) {
        NetworkMember member = network.activeMember(handle);
        if (restricted(member.getUserId(), viewer.getUserId(), NetworkRestrictionKind.BLOCK)) {
            throw new ResourceNotFoundException(NetworkService.NOT_FOUND);
        }
        return member;
    }

    private boolean restricted(Long ownerId, Long targetId, NetworkRestrictionKind kind) {
        return restrictions.findByOwnerIdAndTargetIdAndKind(ownerId, targetId, kind).isPresent();
    }

    /** The members' cards, but those blocking the viewer or blocked by them. */
    private List<NetworkDtos.MemberCard> seen(Long viewerId, List<NetworkMember> listed) {
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(viewerId));
        return listed.stream().filter(member -> !blocked.contains(member.getUserId())).map(network::liveCard).flatMap(Optional::stream).toList();
    }

    private NetworkDtos.MemberPage page(NetworkMember viewer, NetworkMember member) {
        NetworkDtos.MemberCard card = network.card(member);
        boolean own = viewer.getUserId().equals(member.getUserId());
        // Only the skills the member's card shows, so sharing reveals nothing hidden.
        Set<String> viewerSkills = network.card(viewer).stack().stream().map(skill -> skill.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        List<String> shared = own ? List.of() : card.stack().stream().filter(skill -> viewerSkills.contains(skill.toLowerCase(Locale.ROOT)))
            .toList();
        return new NetworkDtos.MemberPage(card, follows.followers(member.getUserId()).size(), follows.following(member.getUserId()).size(),
            shared, follows.findByFollowerIdAndFollowedId(viewer.getUserId(), member.getUserId()).isPresent(),
            follows.findByFollowerIdAndFollowedId(member.getUserId(), viewer.getUserId()).isPresent(), own,
            restricted(viewer.getUserId(), member.getUserId(), NetworkRestrictionKind.MUTE),
            restricted(viewer.getUserId(), member.getUserId(), NetworkRestrictionKind.BLOCK), member.isPrivate(),
            requests.findByRequesterIdAndMemberId(viewer.getUserId(), member.getUserId()).isPresent(), member.getPinnedPostId());
    }

    /**
     * The members whose posts the reader may not read: those in the network with a private card the reader neither holds
     * nor follows.
     */
    Set<Long> locked(Long readerId) {
        Set<Long> locked = new HashSet<>();
        members.findByActiveTrueAndPrivateCardTrue().forEach(member -> locked.add(member.getUserId()));
        if (locked.isEmpty()) {
            return locked;
        }
        locked.remove(readerId);
        follows.following(readerId).forEach(member -> locked.remove(member.getUserId()));
        return locked;
    }
}
