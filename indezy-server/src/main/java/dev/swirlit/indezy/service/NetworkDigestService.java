package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkFollow;
import dev.swirlit.indezy.model.NetworkLike;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPageView;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.DigestSection;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkLikeRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPageViewRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The member network's week for the weekly digest (see docs/features.md#weekly-digest): the posts most reacted to among
 * the members followed, the missions passed on that match the member's card, their new followers and who viewed their page,
 * each section as the member kept it; nothing for an account outside the network.
 */
@Service
@RequiredArgsConstructor
public class NetworkDigestService {

    /** How many characters of a post the digest quotes. */
    static final int EXCERPT = 140;
    /** How many new followers it names. */
    static final int NAMED = 3;

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkFollowRepository follows;
    private final NetworkPostRepository posts;
    private final NetworkLikeRepository likes;
    private final NetworkPageViewRepository views;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkPostService postService;

    @Transactional(readOnly = true)
    public NetworkDtos.NetworkWeek week(Long userId, LocalDateTime since, Set<DigestSection> kept, int max) {
        Optional<NetworkMember> member = members.findByUserId(userId).filter(NetworkMember::isActive);
        if (member.isEmpty()) {
            return NetworkDtos.NetworkWeek.NONE;
        }
        Set<Long> blocked = new HashSet<>(restrictions.blockedEitherWay(userId));
        Map<Long, Optional<NetworkDtos.MemberCard>> cards = new HashMap<>();
        List<NetworkDtos.DigestLine> followed = kept.contains(DigestSection.NETWORK_FOLLOWED) ? followed(userId, since, blocked, cards, max) : List.of();
        List<NetworkDtos.DigestLine> missions = kept.contains(DigestSection.NETWORK_MISSIONS) ?
            missions(userId, member.get(), since, blocked, cards, max) : List.of();
        long newFollowers = 0;
        List<String> names = List.of();
        long viewers = 0;
        if (kept.contains(DigestSection.NETWORK_ACTIVITY)) {
            List<NetworkFollow> recent = follows.findByFollowedIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since).stream()
                .filter(follow -> !blocked.contains(follow.getFollowerId())).toList();
            newFollowers = recent.size();
            names = recent.stream().map(follow -> card(follow.getFollowerId(), cards)).flatMap(Optional::stream).limit(NAMED)
                .map(card -> (card.firstName() + " " + card.lastName()).strip()).toList();
            viewers = views.findByMemberIdAndViewedAtAfterOrderByViewedAtDesc(userId, since).stream().map(NetworkPageView::getViewerId)
                .filter(viewer -> !blocked.contains(viewer)).distinct().count();
        }
        return new NetworkDtos.NetworkWeek(member.get().getHandle(), followed, missions, newFollowers, names, viewers);
    }

    /** The week's posts of the members followed that drew reactions, the most reacted to first. */
    private List<NetworkDtos.DigestLine> followed(Long userId, LocalDateTime since, Set<Long> blocked, Map<Long, Optional<NetworkDtos.MemberCard>> cards,
                                                  int max) {
        List<Long> authors = follows.findByFollowerId(userId).stream().map(NetworkFollow::getFollowedId).filter(id -> !blocked.contains(id)).toList();
        if (authors.isEmpty()) {
            return List.of();
        }
        Set<Long> closed = postService.closedCommunities(userId);
        List<NetworkPost> found = posts.findTop500ByAuthorIdInAndCreatedAtAfterOrderByCreatedAtDesc(authors, since).stream()
            .filter(post -> post.getCommunityId() == null || !closed.contains(post.getCommunityId()))
            .filter(post -> card(post.getAuthorId(), cards).isPresent()).toList();
        Map<Long, Long> reactions = reactions(found, blocked);
        return found.stream().filter(post -> reactions.getOrDefault(post.getId(), 0L) > 0)
            .sorted(Comparator.comparing((NetworkPost post) -> reactions.get(post.getId())).thenComparing(NetworkPost::getCreatedAt).reversed())
            .limit(max).map(post -> line(post, cards, reactions.get(post.getId()))).toList();
    }

    /** The week's missions passed on that match the member's card, the newest first. */
    private List<NetworkDtos.DigestLine> missions(Long userId, NetworkMember member, LocalDateTime since, Set<Long> blocked,
                                                  Map<Long, Optional<NetworkDtos.MemberCard>> cards, int max) {
        Optional<NetworkDtos.MemberCard> mine = network.liveCard(member);
        if (mine.isEmpty()) {
            return List.of();
        }
        List<Long> excluded = new ArrayList<>(blocked);
        excluded.add(userId);
        Set<Long> closed = postService.closedCommunities(userId);
        return posts.findTop500ByKindAndAuthorIdNotInOrderByCreatedAtDescIdDesc(NetworkPostKind.MISSION, excluded).stream()
            .filter(post -> post.getCreatedAt().isAfter(since))
            .filter(post -> post.getCommunityId() == null || !closed.contains(post.getCommunityId()))
            .filter(post -> card(post.getAuthorId(), cards).isPresent())
            .filter(post -> NetworkMissionAlerts.matches(NetworkPostKinds.toDto(post.getDetails()), mine.get()))
            .limit(max).map(post -> line(post, cards, 0)).toList();
    }

    private Map<Long, Long> reactions(List<NetworkPost> found, Set<Long> blocked) {
        if (found.isEmpty()) {
            return Map.of();
        }
        return likes.findByPostIdIn(found.stream().map(NetworkPost::getId).toList()).stream()
            .filter(like -> !blocked.contains(like.getUserId()))
            .collect(Collectors.groupingBy(NetworkLike::getPostId, Collectors.counting()));
    }

    private Optional<NetworkDtos.MemberCard> card(Long userId, Map<Long, Optional<NetworkDtos.MemberCard>> cards) {
        return cards.computeIfAbsent(userId, id -> members.findByUserId(id).filter(NetworkMember::isActive).flatMap(network::liveCard));
    }

    private NetworkDtos.DigestLine line(NetworkPost post, Map<Long, Optional<NetworkDtos.MemberCard>> cards, long reactions) {
        NetworkDtos.MemberCard author = card(post.getAuthorId(), cards).orElseThrow();
        String text = post.getText() == null ? "" : post.getText().replaceAll("\\s+", " ").strip();
        return new NetworkDtos.DigestLine(post.getId(), (author.firstName() + " " + author.lastName()).strip(),
            text.length() > EXCERPT ? text.substring(0, EXCERPT - 1) + "…" : text, reactions);
    }
}
