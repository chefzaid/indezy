package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkFollow;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Members following members (see docs/features.md#member-network): a member's page with its counts and the skills it
 * shares with the viewer, following and unfollowing, the lists of followers and following, and removing a follower.
 * Members who left the network leave every count and list while they are away.
 */
@Service
@RequiredArgsConstructor
public class NetworkFollowService {

    private final NetworkService network;
    private final NetworkFollowRepository follows;

    @Transactional(readOnly = true)
    public NetworkDtos.MemberPage page(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        return page(viewer, network.activeMember(handle));
    }

    @Transactional
    public NetworkDtos.MemberPage follow(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        NetworkMember member = network.activeMember(handle);
        if (viewer.getUserId().equals(member.getUserId())) {
            throw new IllegalArgumentException("A member cannot follow themselves");
        }
        if (follows.findByFollowerIdAndFollowedId(viewerId, member.getUserId()).isEmpty()) {
            NetworkFollow follow = new NetworkFollow();
            follow.setFollowerId(viewerId);
            follow.setFollowedId(member.getUserId());
            follows.save(follow);
        }
        return page(viewer, member);
    }

    @Transactional
    public NetworkDtos.MemberPage unfollow(Long viewerId, String handle) {
        NetworkMember viewer = network.requireMember(viewerId);
        NetworkMember member = network.activeMember(handle);
        follows.findByFollowerIdAndFollowedId(viewerId, member.getUserId()).ifPresent(follows::delete);
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
        network.requireMember(viewerId);
        return follows.followers(network.activeMember(handle).getUserId()).stream().map(network::card).toList();
    }

    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> following(Long viewerId, String handle) {
        network.requireMember(viewerId);
        return follows.following(network.activeMember(handle).getUserId()).stream().map(network::card).toList();
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
            follows.findByFollowerIdAndFollowedId(member.getUserId(), viewer.getUserId()).isPresent(), own);
    }
}
