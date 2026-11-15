package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkLike;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkTagFollow;
import dev.swirlit.indezy.model.NetworkUseful;
import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import dev.swirlit.indezy.repository.NetworkLikeRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.NetworkTagFollowRepository;
import dev.swirlit.indezy.repository.NetworkUsefulRepository;
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
 * city their card shows) and by how useful and liked they were, the older ones lower.
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
    private final NetworkUsefulRepository usefuls;
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
        List<NetworkPost> recent = posts.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, SEARCHED)).stream()
            .filter(post -> !excluded.contains(post.getAuthorId()) && post.getCreatedAt() != null && post.getCreatedAt().isAfter(since))
            .filter(post -> authors.computeIfAbsent(post.getAuthorId(), id -> members.findByUserId(id).filter(NetworkMember::isActive))
                .isPresent()).toList();
        List<Long> ids = recent.stream().map(NetworkPost::getId).toList();
        Map<Long, Long> liked = likes.findByPostIdIn(ids).stream().filter(like -> !excluded.contains(like.getUserId()))
            .collect(Collectors.groupingBy(NetworkLike::getPostId, Collectors.counting()));
        Map<Long, Long> useful = usefuls.findByPostIdIn(ids).stream().filter(found -> !excluded.contains(found.getUserId()))
            .collect(Collectors.groupingBy(NetworkUseful::getPostId, Collectors.counting()));
        Reader taste = new Reader(tagFollows.findByUserIdOrderByTagAsc(userId).stream().map(NetworkTagFollow::getTag).collect(Collectors.toSet()),
            card.stack().stream().map(skill -> skill.toLowerCase(Locale.ROOT)).collect(Collectors.toSet()),
            card.city() == null ? null : card.city().toLowerCase(Locale.ROOT));
        Map<Long, Double> scores = new HashMap<>();
        for (NetworkPost post : recent) {
            String city = authors.get(post.getAuthorId()).flatMap(network::liveCard).map(NetworkDtos.MemberCard::city).orElse(null);
            double days = Duration.between(post.getCreatedAt(), now).toHours() / 24.0;
            scores.put(post.getId(), score(post, taste, city, liked.getOrDefault(post.getId(), 0L), useful.getOrDefault(post.getId(), 0L), days));
        }
        List<NetworkPost> ranked = recent.stream().sorted(Comparator.comparing((NetworkPost post) -> scores.get(post.getId())).reversed()
            .thenComparing(NetworkPost::getCreatedAt, Comparator.reverseOrder())).toList();
        int from = Math.max(0, page) * PAGE_SIZE;
        List<NetworkPost> shown = ranked.stream().skip(from).limit(PAGE_SIZE).toList();
        return postService.page(new PageImpl<>(shown, PageRequest.of(Math.max(0, page), PAGE_SIZE), ranked.size()), userId);
    }

    /** What the reader follows and their card shows: the tags followed, the skills and the city, lower-case. */
    record Reader(Set<String> tags, Set<String> skills, String city) {
    }

    /**
     * 3 a tag the reader follows, 2 a tag among their skills, 2 the author in their city, 1 a member who found it useful
     * (5 at most) and half a like (5 at most), the whole lowered as the post ages: divided by 1 plus a third of its age in days.
     */
    static double score(NetworkPost post, Reader reader, String authorCity, long likeCount, long usefulCount, double days) {
        double score = 0;
        for (String tag : post.getTags()) {
            score += reader.tags().contains(tag) ? 3 : 0;
            score += reader.skills().contains(tag) ? 2 : 0;
        }
        if (reader.city() != null && authorCity != null && Objects.equals(reader.city(), authorCity.toLowerCase(Locale.ROOT))) {
            score += 2;
        }
        score += Math.min(usefulCount, 5) + Math.min(likeCount, 5) / 2.0;
        return score / (1 + Math.max(0, days) / 3);
    }
}
