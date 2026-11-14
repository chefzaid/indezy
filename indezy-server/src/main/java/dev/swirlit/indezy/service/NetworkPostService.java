package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkComment;
import dev.swirlit.indezy.model.NetworkLike;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkLikeRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Members' posts (see docs/features.md#member-network): text with the #tags it names, read in the home feed (the
 * reader's own posts and those of the members they follow, but those muted or blocked either way) and on each member's
 * page, the newest first. A member deletes their own posts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkPostService {

    static final int PAGE_SIZE = 20;
    static final String NOT_FOUND = "No such post";
    static final int MAX_TAGS = 10;
    /** A #tag: a letter or digit, then up to 39 letters, digits, dashes or underscores, not inside a word. */
    private static final Pattern TAG = Pattern.compile("(?<![\\p{L}\\p{N}_#])#([\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,39})");

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final NetworkPostRepository posts;
    private final NetworkMemberRepository members;
    private final NetworkFollowRepository followRepository;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkLikeRepository likes;
    private final NetworkCommentRepository comments;

    @Transactional
    public NetworkDtos.Post post(Long userId, NetworkDtos.NewPost request) {
        NetworkMember author = network.requireMember(userId);
        NetworkPost post = new NetworkPost();
        post.setAuthorId(userId);
        post.setText(request.text().strip());
        post.setTags(tags(post.getText()));
        posts.save(post);
        log.info("Member {} posted {}", userId, post.getId());
        return toDto(post, network.card(author), userId, List.of(), 0);
    }

    /** Deletes the reader's own post; another member's, like an unknown one, is not found. */
    @Transactional
    public void delete(Long userId, Long postId) {
        network.requireMember(userId);
        NetworkPost post = posts.findByIdAndAuthorId(postId, userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        likes.deleteByPostId(post.getId());
        comments.deleteByPostId(post.getId());
        posts.delete(post);
    }

    /** The reader's posts and those of the members they follow, still in the network, but those muted or blocked either way. */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts feed(Long userId, int page) {
        network.requireMember(userId);
        Set<Long> authors = new HashSet<>();
        authors.add(userId);
        followRepository.following(userId).forEach(member -> authors.add(member.getUserId()));
        restrictions.targets(userId, NetworkRestrictionKind.MUTE).forEach(member -> authors.remove(member.getUserId()));
        authors.removeAll(restrictions.blockedEitherWay(userId));
        return page(authors, userId, page);
    }

    /** A member's posts, for the members their page shows to. */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts memberPosts(Long viewerId, String handle, int page) {
        NetworkMember member = follows.visible(network.requireMember(viewerId), handle);
        return page(List.of(member.getUserId()), viewerId, page);
    }

    /** Every post of the account, the newest first, for its data export (without the author card: it is the account's). */
    @Transactional(readOnly = true)
    public List<NetworkDtos.Post> export(Long userId) {
        return posts.findByAuthorIdInOrderByCreatedAtDescIdDesc(List.of(userId), Pageable.unpaged()).stream()
            .map(post -> toDto(post, null, userId, List.of(), 0)).toList();
    }

    private NetworkDtos.Posts page(Collection<Long> authors, Long readerId, int page) {
        Page<NetworkPost> found = posts.findByAuthorIdInOrderByCreatedAtDescIdDesc(authors, PageRequest.of(Math.max(0, page), PAGE_SIZE));
        Map<Long, Optional<NetworkDtos.MemberCard>> cards = new HashMap<>();
        List<Long> ids = found.getContent().stream().map(NetworkPost::getId).toList();
        Map<Long, List<NetworkLike>> liked = likes.findByPostIdIn(ids).stream().collect(Collectors.groupingBy(NetworkLike::getPostId));
        Map<Long, List<NetworkComment>> commented = comments.findByPostIdIn(ids).stream()
            .collect(Collectors.groupingBy(NetworkComment::getPostId));
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(readerId));
        List<NetworkDtos.Post> shown = found.getContent().stream()
            .map(post -> cards.computeIfAbsent(post.getAuthorId(), this::authorCard)
                .map(card -> toDto(post, card, readerId, liked.getOrDefault(post.getId(), List.of()),
                    seenComments(post, readerId, blocked, commented.getOrDefault(post.getId(), List.of())))))
            .flatMap(Optional::stream).toList();
        return new NetworkDtos.Posts(shown, found.getNumber(), found.hasNext());
    }

    /** How many of the comments the reader sees: not hidden (but to the post's author), nor written by a member blocked either way. */
    static int seenComments(NetworkPost post, Long readerId, Set<Long> blocked, List<NetworkComment> postComments) {
        return (int) postComments.stream().filter(comment -> visible(post, comment, readerId, blocked)).count();
    }

    /** Whether the reader sees the comment: not hidden, unless they wrote the post, and not by a member blocked either way. */
    static boolean visible(NetworkPost post, NetworkComment comment, Long readerId, Set<Long> blocked) {
        return !blocked.contains(comment.getAuthorId()) && (!comment.isHidden() || post.getAuthorId().equals(readerId));
    }

    /** The author's card while they are in the network; their posts are hidden while they are away. */
    Optional<NetworkDtos.MemberCard> authorCard(Long authorId) {
        return members.findByUserId(authorId).filter(NetworkMember::isActive).flatMap(network::liveCard);
    }

    private static NetworkDtos.Post toDto(NetworkPost post, NetworkDtos.MemberCard author, Long readerId, List<NetworkLike> postLikes,
                                          int seenComments) {
        return new NetworkDtos.Post(post.getId(), author, post.getText(), List.copyOf(post.getTags()), post.getCreatedAt(),
            post.getAuthorId().equals(readerId), postLikes.size(), postLikes.stream().anyMatch(like -> like.getUserId().equals(readerId)),
            seenComments, post.isCommentsClosed());
    }

    /** Likes a post the reader can read, or no longer; returns it as the reader now reads it. */
    @Transactional
    public NetworkDtos.Post like(Long userId, Long postId, boolean liked) {
        NetworkPost post = readable(userId, postId);
        Optional<NetworkLike> existing = likes.findByPostIdAndUserId(postId, userId);
        if (liked && existing.isEmpty()) {
            NetworkLike like = new NetworkLike();
            like.setPostId(postId);
            like.setUserId(userId);
            likes.save(like);
        } else if (!liked) {
            existing.ifPresent(likes::delete);
        }
        return toDto(post, authorCard(post.getAuthorId()).orElseThrow(), userId, likes.findByPostIdIn(List.of(postId)),
            seenComments(post, userId, Set.copyOf(restrictions.blockedEitherWay(userId)), comments.findByPostIdIn(List.of(postId))));
    }

    /** The members who like a post the reader can read, but those blocked either way. */
    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> likers(Long userId, Long postId) {
        readable(userId, postId);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        return likes.likers(postId).stream().filter(member -> !blocked.contains(member.getUserId())).map(network::liveCard)
            .flatMap(Optional::stream).toList();
    }

    /** A post the reader can read: its author in the network, neither blocking the other; not found otherwise. */
    NetworkPost readable(Long userId, Long postId) {
        network.requireMember(userId);
        NetworkPost post = posts.findById(postId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (authorCard(post.getAuthorId()).isEmpty() || restrictions.blockedEitherWay(userId).contains(post.getAuthorId())) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return post;
    }

    /** The #tags the text names, lower-case, each once and at most {@value #MAX_TAGS}. */
    static Set<String> tags(String text) {
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = TAG.matcher(text);
        while (matcher.find() && tags.size() < MAX_TAGS) {
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tags;
    }
}
