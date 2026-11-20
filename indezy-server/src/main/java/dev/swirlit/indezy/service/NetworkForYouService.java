package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkLike;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkPostDismissal;
import dev.swirlit.indezy.model.NetworkTagFollow;
import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import dev.swirlit.indezy.repository.NetworkLikeRepository;
import dev.swirlit.indezy.repository.NetworkPostDismissalRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.NetworkTagFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The **For you** tab of the home feed (see docs/features.md#member-network): the latest posts of the last weeks from
 * every member the reader can read, ranked by what they share with the reader (the tags they follow, the skills and the
 * city their card shows) and by how liked they were, the older ones lower.
 */
@Service
@RequiredArgsConstructor
public class NetworkForYouService {

    static final int SEARCHED = 500;
    static final Duration RECENT = Duration.ofDays(30);
    static final int PAGE_SIZE = 20;

    private final NetworkService network;
    private final NetworkPostService postService;
    private final NetworkFollowService follows;
    private final NetworkPostRepository posts;
    private final NetworkMemberRepository members;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkTagFollowRepository tagFollows;
    private final NetworkLikeRepository likes;
    private final NetworkPostDismissalRepository dismissals;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NetworkDtos.Posts forYou(Long userId, int page) {
        NetworkMember reader = network.requireMember(userId);
        NetworkDtos.MemberCard card = network.card(reader);
        Set<Long> excluded = new HashSet<>(restrictions.blockedEitherWay(userId));
        restrictions.targets(userId, NetworkRestrictionKind.MUTE).forEach(member -> excluded.add(member.getUserId()));
        excluded.addAll(follows.locked(userId));
        excluded.add(userId);
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
        LocalDateTime since = now.minus(RECENT);
        Map<Long, Optional<NetworkMember>> authors = new HashMap<>();
        Dismissed dismissed = dismissed(userId);
        List<NetworkPost> recent = posts.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, SEARCHED)).stream()
            .filter(post -> !dismissed.posts().contains(post.getId()))
            .filter(post -> !excluded.contains(post.getAuthorId()) && post.getCreatedAt() != null && post.getCreatedAt().isAfter(since))
            .filter(post -> authors.computeIfAbsent(post.getAuthorId(), id -> members.findByUserId(id).filter(NetworkMember::isActive))
                .isPresent()).toList();
        List<Long> ids = recent.stream().map(NetworkPost::getId).toList();
        Map<Long, Long> liked = likes.findByPostIdIn(ids).stream().filter(like -> !excluded.contains(like.getUserId()))
            .collect(Collectors.groupingBy(NetworkLike::getPostId, Collectors.counting()));
        Reader taste = new Reader(tagFollows.findByUserIdOrderByTagAsc(userId).stream().map(NetworkTagFollow::getTag).collect(Collectors.toSet()),
            card.stack().stream().map(skill -> skill.toLowerCase(Locale.ROOT)).collect(Collectors.toSet()),
            card.city() == null ? null : card.city().toLowerCase(Locale.ROOT));
        Map<Long, Double> scores = new HashMap<>();
        for (NetworkPost post : recent) {
            String city = authors.get(post.getAuthorId()).flatMap(network::liveCard).map(NetworkDtos.MemberCard::city).orElse(null);
            double days = Duration.between(post.getCreatedAt(), now).toHours() / 24.0;
            scores.put(post.getId(), score(post, taste, city, liked.getOrDefault(post.getId(), 0L), days, dismissed.penalty(post)));
        }
        List<NetworkPost> ranked = recent.stream().sorted(Comparator.comparing((NetworkPost post) -> scores.get(post.getId())).reversed()
            .thenComparing(NetworkPost::getCreatedAt, Comparator.reverseOrder())).toList();
        int from = Math.max(0, page) * PAGE_SIZE;
        List<NetworkPost> shown = ranked.stream().skip(from).limit(PAGE_SIZE).toList();
        return postService.page(new PageImpl<>(shown, PageRequest.of(Math.max(0, page), PAGE_SIZE), ranked.size()), userId);
    }

    /** **Not interested** in a post: hidden from For you, counting against its author and tags; or no longer. */
    @Transactional
    public void dismiss(Long userId, Long postId, boolean dismissed) {
        NetworkPost post = postService.readable(userId, postId);
        Optional<NetworkPostDismissal> known = dismissals.findByUserIdAndPostId(userId, post.getId());
        if (dismissed && known.isEmpty()) {
            NetworkPostDismissal dismissal = new NetworkPostDismissal();
            dismissal.setUserId(userId);
            dismissal.setPostId(post.getId());
            dismissals.save(dismissal);
        } else if (!dismissed) {
            known.ifPresent(dismissals::delete);
        }
    }

    /** The posts the reader dismissed, and how many each author and tag account for among them. */
    private Dismissed dismissed(Long userId) {
        List<Long> ids = dismissals.findTop200ByUserIdOrderByCreatedAtDesc(userId).stream().map(NetworkPostDismissal::getPostId).toList();
        Map<Long, Integer> byAuthor = new HashMap<>();
        Map<String, Integer> byTag = new HashMap<>();
        for (NetworkPost post : posts.findAllById(ids)) {
            byAuthor.merge(post.getAuthorId(), 1, Integer::sum);
            post.getTags().forEach(tag -> byTag.merge(tag, 1, Integer::sum));
        }
        return new Dismissed(Set.copyOf(ids), byAuthor, byTag);
    }

    /** The reader's dismissals: the posts, and per author and tag how many. */
    record Dismissed(Set<Long> posts, Map<Long, Integer> byAuthor, Map<String, Integer> byTag) {

        /** 2 a post of the same author dismissed (6 at most), 1 a dismissed post naming each of its tags (3 at most a tag). */
        double penalty(NetworkPost post) {
            double penalty = Math.min(6, 2 * byAuthor.getOrDefault(post.getAuthorId(), 0));
            for (String tag : post.getTags()) {
                penalty += Math.min(3, byTag.getOrDefault(tag, 0));
            }
            return penalty;
        }
    }

    /** What the reader follows and their card shows: the tags followed, the skills and the city, lower-case. */
    record Reader(Set<String> tags, Set<String> skills, String city) {
    }

    /**
     * 3 a tag the reader follows, 2 a tag among their skills, 2 the author in their city, 1 a member who reacted to it (5 at
     * most), the whole lowered as the post ages: divided by 1 plus a third of its age in days.
     */
    static double score(NetworkPost post, Reader reader, String authorCity, long likeCount, double days) {
        return score(post, reader, authorCity, likeCount, days, 0);
    }

    /** The same, less what the reader's **Not interested** count against the post's author and tags. */
    static double score(NetworkPost post, Reader reader, String authorCity, long likeCount, double days, double penalty) {
        double score = -penalty;
        for (String tag : post.getTags()) {
            score += reader.tags().contains(tag) ? 3 : 0;
            score += reader.skills().contains(tag) ? 2 : 0;
        }
        if (reader.city() != null && authorCity != null && Objects.equals(reader.city(), authorCity.toLowerCase(Locale.ROOT))) {
            score += 2;
        }
        score += Math.min(likeCount, 5);
        return score / (1 + Math.max(0, days) / 3);
    }
}
