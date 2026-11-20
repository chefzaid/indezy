package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkTagFollow;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.NetworkTagFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the network's home reads (see docs/features.md#member-network): the home feed, whether newer posts reached it,
 * a tag's posts, the tags trending among the posts the reader can read, and the tags in use to complete a #tag.
 */
@Service
@RequiredArgsConstructor
public class NetworkFeedService {

    /** How far back the trending tags look. */
    static final Duration TRENDING_WINDOW = Duration.ofDays(7);
    static final int TRENDING_SHOWN = 8;
    static final int SUGGESTED = 5;
    /** How many characters a search for posts needs. */
    static final int SEARCHED_AT_LEAST = 2;
    /** A time before any post, standing for "any time" (a null time would not do on PostgreSQL). */
    static final LocalDateTime SEARCH_BEGINNING = LocalDateTime.of(2000, 1, 1, 0, 0);
    /** What a tag may start with: what tags hold, so the search cannot be a pattern. */
    private static final Pattern TAG_START = Pattern.compile("[\\p{L}\\p{N}_-]{0,40}");

    private final NetworkService network;
    private final NetworkPostService postService;
    private final NetworkFollowService follows;
    private final NetworkPostRepository posts;
    private final NetworkFollowRepository followRepository;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkTagFollowRepository tagFollows;
    private final Clock clock;

    /** Where the home feed's posts come from; a value no row holds stands in for an empty list, not valid everywhere. */
    private record Sources(Set<Long> authors, List<String> tags, List<Long> excluded) {
    }

    /**
     * The reader's posts, those of the members they follow and those naming a tag they follow, still in the network, but
     * those of members muted or blocked either way.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts feed(Long userId, int page) {
        network.requireMember(userId);
        Sources sources = sources(userId);
        return postService.page(posts.feed(sources.authors(), sources.tags(), sources.excluded(),
            PageRequest.of(Math.max(0, page), NetworkPostService.PAGE_SIZE)), userId);
    }

    /**
     * The **Missions** tab: the missions members pass on, from every member the reader can read (but their own, and those
     * muted or blocked either way), the newest first, each to add to the reader's pipeline.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts missions(Long userId, int page) {
        network.requireMember(userId);
        Set<Long> left = excluded(userId);
        left.add(userId);
        return postService.page(posts.ofKind(NetworkPostKind.MISSION, List.copyOf(left), PageRequest.of(Math.max(0, page),
            NetworkPostService.PAGE_SIZE)), userId);
    }

    /** The filters of the search for posts: kinds (none for every kind), the last days (none for any time), the members followed only. */
    public record PostFilters(List<NetworkPostKind> kinds, Integer days, boolean followedOnly) {

        public static final PostFilters NONE = new PostFilters(List.of(), null, false);

        public PostFilters {
            kinds = kinds == null ? List.of() : List.copyOf(kinds);
        }
    }

    /** The search for posts without filters. */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts search(Long userId, String typed, int page) {
        return search(userId, typed, page, PostFilters.NONE);
    }

    /**
     * The network's search for posts: those holding what was typed (case and accents aside, a # or @ kept as typed), from
     * every member the reader can read (their own included, but those muted or blocked either way), the newest first;
     * of the kinds asked, in the last days asked, from the members followed only when asked.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts search(Long userId, String typed, int page, PostFilters filters) {
        network.requireMember(userId);
        String phrase = Optional.ofNullable(NetworkPost.fold(typed)).orElse("");
        PageRequest asked = PageRequest.of(Math.max(0, page), NetworkPostService.PAGE_SIZE);
        if (phrase.length() < SEARCHED_AT_LEAST) {
            return new NetworkDtos.Posts(List.of(), asked.getPageNumber(), false);
        }
        String pattern = "%" + phrase.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Long> excluded = placeholder(excluded(userId), -1L);
        List<NetworkPostKind> kinds = filters.kinds().isEmpty() ? List.of(NetworkPostKind.values()) : filters.kinds();
        LocalDateTime since = filters.days() == null ? SEARCH_BEGINNING : LocalDateTime.now(clock).minusDays(Math.max(1, filters.days()));
        if (filters.followedOnly()) {
            List<Long> authors = placeholder(followRepository.following(userId).stream().map(member -> member.getUserId()).toList(), -1L);
            return postService.page(posts.searchFrom(pattern, authors, excluded, kinds, since, asked), userId);
        }
        return postService.page(posts.searchFiltered(pattern, excluded, kinds, since, asked), userId);
    }

    /** How many posts by others reached the home feed after the given time, for the feed to offer them. */
    @Transactional(readOnly = true)
    public NetworkDtos.Newer newer(Long userId, LocalDateTime since) {
        network.requireMember(userId);
        Sources sources = sources(userId);
        return new NetworkDtos.Newer(posts.feedNewer(sources.authors(), sources.tags(), sources.excluded(), userId, since));
    }

    /**
     * A tag's posts, from every member the reader can read, but those muted or blocked either way: the newest first, or
     * the most liked first.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts tagPosts(Long userId, String tag, int page, boolean byLikes) {
        network.requireMember(userId);
        List<Long> left = placeholder(excluded(userId), -1L);
        PageRequest asked = PageRequest.of(Math.max(0, page), NetworkPostService.PAGE_SIZE);
        return postService.page(byLikes ? posts.tagByLikes(tag, left, asked) : posts.feed(List.of(-1L), List.of(tag), left, asked), userId);
    }

    /**
     * The tags most named in the last seven days by the posts the reader can read (by members still in the network,
     * neither blocking the other, a private card only when followed), each with how many posts name it.
     */
    @Transactional(readOnly = true)
    public List<NetworkDtos.TrendingTag> trending(Long userId) {
        network.requireMember(userId);
        Set<Long> unseen = new HashSet<>(restrictions.blockedEitherWay(userId));
        unseen.addAll(follows.locked(userId));
        Map<Long, Boolean> present = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (NetworkPost post : posts.recentWithTags(LocalDateTime.now(clock).minus(TRENDING_WINDOW))) {
            boolean readable = !unseen.contains(post.getAuthorId()) &&
                present.computeIfAbsent(post.getAuthorId(), author -> postService.authorCard(author).isPresent());
            if (readable) {
                post.getTags().forEach(tag -> counts.merge(tag, 1L, Long::sum));
            }
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(TRENDING_SHOWN).map(entry -> new NetworkDtos.TrendingTag(entry.getKey(), entry.getValue())).toList();
    }

    /** The tags in use starting with what was typed (a # aside), the most used first, five at most. */
    @Transactional(readOnly = true)
    public List<String> suggestTags(Long userId, String typed) {
        network.requireMember(userId);
        String start = Optional.ofNullable(typed).orElse("").strip().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        if (!TAG_START.matcher(start).matches()) {
            return List.of();
        }
        return posts.tagsStartingWith(start.replace("_", "\\_") + "%", PageRequest.of(0, SUGGESTED));
    }

    private Sources sources(Long userId) {
        Set<Long> authors = new HashSet<>();
        authors.add(userId);
        followRepository.following(userId).forEach(member -> authors.add(member.getUserId()));
        Set<Long> excluded = excluded(userId);
        authors.removeAll(excluded);
        List<String> tags = tagFollows.findByUserIdOrderByTagAsc(userId).stream().map(NetworkTagFollow::getTag).toList();
        return new Sources(authors, placeholder(tags, ""), placeholder(excluded, -1L));
    }

    /** The members muted or blocked either way, and the private cards the reader does not follow. */
    Set<Long> excluded(Long userId) {
        Set<Long> excluded = new HashSet<>(restrictions.blockedEitherWay(userId));
        restrictions.targets(userId, NetworkRestrictionKind.MUTE).forEach(member -> excluded.add(member.getUserId()));
        excluded.addAll(follows.locked(userId));
        return excluded;
    }

    private static <T> List<T> placeholder(Collection<T> values, T none) {
        return values.isEmpty() ? List.of(none) : List.copyOf(values);
    }
}
