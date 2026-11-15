package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkLike;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkTagFollow;
import dev.swirlit.indezy.repository.NetworkLikeRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.NetworkTagFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tags of the member network (see docs/features.md#member-network): a tag's page, with its posts and the members whose
 * cards show the skill, and the tags a member follows, whose posts reach their home feed and, the most liked, their weekly
 * digest.
 */
@Service
@RequiredArgsConstructor
public class NetworkTagService {

    static final int MEMBERS_SHOWN = 12;
    static final int EXCERPT = 100;
    private static final Pattern TAG = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,39}");

    private final NetworkService network;
    private final NetworkPostService posts;
    private final NetworkDirectoryService directory;
    private final NetworkTagFollowRepository follows;
    private final NetworkMemberRepository members;
    private final NetworkPostRepository postRepository;
    private final NetworkLikeRepository likes;
    private final NetworkRestrictionRepository restrictions;

    @Transactional(readOnly = true)
    public NetworkDtos.TagPage page(Long userId, String tag, int page) {
        String name = normalized(tag);
        List<NetworkDtos.MemberCard> shown = directory.search(userId, new NetworkDirectoryService.Search(name, null, null, null, 0))
            .members().stream().limit(MEMBERS_SHOWN).toList();
        return new NetworkDtos.TagPage(name, follows.findByUserIdAndTag(userId, name).isPresent(), posts.tagPosts(userId, name, page), shown);
    }

    /** Follows a tag, or no longer; answers the tags the member follows, in order. */
    @Transactional
    public List<String> follow(Long userId, String tag, boolean followed) {
        network.requireMember(userId);
        String name = normalized(tag);
        var existing = follows.findByUserIdAndTag(userId, name);
        if (followed && existing.isEmpty()) {
            NetworkTagFollow follow = new NetworkTagFollow();
            follow.setUserId(userId);
            follow.setTag(name);
            follows.save(follow);
        } else if (!followed) {
            existing.ifPresent(follows::delete);
        }
        return followed(userId);
    }

    @Transactional(readOnly = true)
    public List<String> followed(Long userId) {
        network.requireMember(userId);
        return follows.findByUserIdOrderByTagAsc(userId).stream().map(NetworkTagFollow::getTag).toList();
    }

    /**
     * The most liked post since then of each tag the member follows, in the tags' order, up to {@code max}: by another
     * member still in the network, neither blocking the other, liked at least once and told under one tag only. None for
     * an account away from the network.
     */
    @Transactional(readOnly = true)
    public List<NetworkDtos.DigestPost> weekTop(Long userId, LocalDateTime since, int max) {
        if (members.findByUserId(userId).filter(NetworkMember::isActive).isEmpty()) {
            return List.of();
        }
        List<String> tags = follows.findByUserIdOrderByTagAsc(userId).stream().map(NetworkTagFollow::getTag).toList();
        if (tags.isEmpty()) {
            return List.of();
        }
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        Map<Long, Optional<NetworkDtos.MemberCard>> authors = new HashMap<>();
        List<NetworkPost> found = postRepository.taggedSince(tags, since).stream()
            .filter(post -> !post.getAuthorId().equals(userId) && !blocked.contains(post.getAuthorId()))
            .filter(post -> authors.computeIfAbsent(post.getAuthorId(), posts::authorCard).isPresent()).toList();
        Map<Long, Long> liked = likes.findByPostIdIn(found.stream().map(NetworkPost::getId).toList()).stream()
            .filter(like -> !blocked.contains(like.getUserId()))
            .collect(Collectors.groupingBy(NetworkLike::getPostId, Collectors.counting()));
        Comparator<NetworkPost> best = Comparator.comparing((NetworkPost post) -> liked.getOrDefault(post.getId(), 0L))
            .thenComparing(NetworkPost::getCreatedAt).reversed();
        Set<Long> told = new HashSet<>();
        List<NetworkDtos.DigestPost> top = new ArrayList<>();
        for (String tag : tags) {
            found.stream().filter(post -> post.getTags().contains(tag) && liked.getOrDefault(post.getId(), 0L) > 0 && !told.contains(post.getId()))
                .min(best).ifPresent(post -> {
                    told.add(post.getId());
                    NetworkDtos.MemberCard author = authors.get(post.getAuthorId()).orElseThrow();
                    String text = post.getText().replaceAll("\\s+", " ").strip();
                    top.add(new NetworkDtos.DigestPost(tag, (author.firstName() + " " + author.lastName()).strip(),
                        text.length() > EXCERPT ? text.substring(0, EXCERPT - 1) + "…" : text, liked.get(post.getId())));
                });
        }
        return top.stream().limit(max).toList();
    }

    /** The tag as posts keep it: lower-case, without the #; refused when it is not one. */
    static String normalized(String tag) {
        String name = tag == null ? "" : tag.strip().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        if (!TAG.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a tag: letters, digits, dashes or underscores, 40 at most");
        }
        return name;
    }
}
