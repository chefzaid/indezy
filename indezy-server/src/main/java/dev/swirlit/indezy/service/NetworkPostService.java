package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkBookmark;
import dev.swirlit.indezy.model.NetworkComment;
import dev.swirlit.indezy.model.NetworkLike;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkPostVersion;
import dev.swirlit.indezy.model.NetworkTagFollow;
import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import dev.swirlit.indezy.repository.NetworkBookmarkRepository;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkLikeRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkPostVersionRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.NetworkTagFollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
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
    /** How long after posting a post can still be edited. */
    static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
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
    private final NetworkPostVersionRepository versions;
    private final NetworkBookmarkRepository bookmarks;
    private final NetworkTagFollowRepository tagFollows;
    private final Clock clock;
    private final NetworkLimits limits;

    @Transactional
    public NetworkDtos.Post post(Long userId, NetworkDtos.NewPost request) {
        NetworkMember author = network.requireMember(userId);
        limits.post(userId);
        NetworkPost post = new NetworkPost();
        post.setAuthorId(userId);
        post.setText(request.text().strip());
        post.setTags(tags(post.getText()));
        posts.save(post);
        log.info("Member {} posted {}", userId, post.getId());
        return toDto(post, network.card(author), userId, List.of(), 0, false);
    }

    /** Deletes the reader's own post; another member's, like an unknown one, is not found. */
    @Transactional
    public void delete(Long userId, Long postId) {
        network.requireMember(userId);
        NetworkPost post = posts.findByIdAndAuthorId(postId, userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        likes.deleteByPostId(post.getId());
        comments.deleteByPostId(post.getId());
        versions.deleteByPostId(post.getId());
        bookmarks.deleteByPostId(post.getId());
        posts.delete(post);
    }

    /**
     * The reader's posts, those of the members they follow and those naming a tag they follow, still in the network, but
     * those of members muted or blocked either way.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts feed(Long userId, int page) {
        network.requireMember(userId);
        Set<Long> authors = new HashSet<>();
        authors.add(userId);
        followRepository.following(userId).forEach(member -> authors.add(member.getUserId()));
        Set<Long> excluded = new HashSet<>(restrictions.blockedEitherWay(userId));
        restrictions.targets(userId, NetworkRestrictionKind.MUTE).forEach(member -> excluded.add(member.getUserId()));
        authors.removeAll(excluded);
        List<String> tags = tagFollows.findByUserIdOrderByTagAsc(userId).stream().map(NetworkTagFollow::getTag).toList();
        // An empty IN list is not valid everywhere: a value no row holds stands in for none.
        Page<NetworkPost> found = posts.feed(authors, tags.isEmpty() ? List.of("") : tags, excluded.isEmpty() ? List.of(-1L) : excluded,
            PageRequest.of(Math.max(0, page), PAGE_SIZE));
        return page(found, userId);
    }

    /** A tag's posts, from every member the reader can read, but those muted or blocked either way. */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts tagPosts(Long userId, String tag, int page) {
        network.requireMember(userId);
        Set<Long> excluded = new HashSet<>(restrictions.blockedEitherWay(userId));
        restrictions.targets(userId, NetworkRestrictionKind.MUTE).forEach(member -> excluded.add(member.getUserId()));
        return page(posts.feed(List.of(-1L), List.of(tag), excluded.isEmpty() ? List.of(-1L) : excluded,
            PageRequest.of(Math.max(0, page), PAGE_SIZE)), userId);
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
            .map(post -> toDto(post, null, userId, List.of(), 0, false)).toList();
    }

    private NetworkDtos.Posts page(Collection<Long> authors, Long readerId, int page) {
        return page(posts.findByAuthorIdInOrderByCreatedAtDescIdDesc(authors, PageRequest.of(Math.max(0, page), PAGE_SIZE)), readerId);
    }

    private NetworkDtos.Posts page(Page<NetworkPost> found, Long readerId) {
        Map<Long, Optional<NetworkDtos.MemberCard>> cards = new HashMap<>();
        List<Long> ids = found.getContent().stream().map(NetworkPost::getId).toList();
        Map<Long, List<NetworkLike>> liked = likes.findByPostIdIn(ids).stream().collect(Collectors.groupingBy(NetworkLike::getPostId));
        Map<Long, List<NetworkComment>> commented = comments.findByPostIdIn(ids).stream()
            .collect(Collectors.groupingBy(NetworkComment::getPostId));
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(readerId));
        Set<Long> saved = bookmarks.findByUserIdAndPostIdIn(readerId, ids).stream().map(NetworkBookmark::getPostId).collect(Collectors.toSet());
        List<NetworkDtos.Post> shown = found.getContent().stream()
            .map(post -> cards.computeIfAbsent(post.getAuthorId(), this::authorCard)
                .map(card -> toDto(post, card, readerId, liked.getOrDefault(post.getId(), List.of()),
                    seenComments(post, readerId, blocked, commented.getOrDefault(post.getId(), List.of())), saved.contains(post.getId()))))
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
                                          int seenComments, boolean bookmarked) {
        return new NetworkDtos.Post(post.getId(), author, post.getText(), List.copyOf(post.getTags()), post.getCreatedAt(),
            post.getAuthorId().equals(readerId), postLikes.size(), postLikes.stream().anyMatch(like -> like.getUserId().equals(readerId)),
            seenComments, post.isCommentsClosed(), post.getEditedAt() != null, bookmarked);
    }

    /** A single post as the reader now reads it. */
    private NetworkDtos.Post reread(NetworkPost post, Long readerId) {
        return toDto(post, authorCard(post.getAuthorId()).orElseThrow(), readerId, likes.findByPostIdIn(List.of(post.getId())),
            seenComments(post, readerId, Set.copyOf(restrictions.blockedEitherWay(readerId)), comments.findByPostIdIn(List.of(post.getId()))),
            bookmarks.findByUserIdAndPostId(readerId, post.getId()).isPresent());
    }

    /** Saves a post the reader can read to their private list, or takes it off. */
    @Transactional
    public NetworkDtos.Post bookmark(Long userId, Long postId, boolean saved) {
        NetworkPost post = readable(userId, postId);
        Optional<NetworkBookmark> existing = bookmarks.findByUserIdAndPostId(userId, postId);
        if (saved && existing.isEmpty()) {
            NetworkBookmark bookmark = new NetworkBookmark();
            bookmark.setUserId(userId);
            bookmark.setPostId(postId);
            bookmarks.save(bookmark);
        } else if (!saved) {
            existing.ifPresent(bookmarks::delete);
        }
        return reread(post, userId);
    }

    /**
     * The reader's saved posts they can still read, the latest saved first, 20 a page, those naming the tag only when one
     * is given; with the tags of all of them, to filter by.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.SavedPosts saved(Long userId, String tag, int page) {
        network.requireMember(userId);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        List<NetworkPost> readable = bookmarks.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
            .map(bookmark -> posts.findById(bookmark.getPostId())).flatMap(Optional::stream)
            .filter(post -> !blocked.contains(post.getAuthorId()) && authorCard(post.getAuthorId()).isPresent()).toList();
        List<String> tags = readable.stream().flatMap(post -> post.getTags().stream()).distinct().sorted().toList();
        String wanted = tag == null || tag.isBlank() ? null : tag.strip().toLowerCase(Locale.ROOT).replaceFirst("^#", "");
        List<NetworkPost> chosen = readable.stream().filter(post -> wanted == null || post.getTags().contains(wanted)).toList();
        int from = Math.max(0, page) * PAGE_SIZE;
        List<NetworkDtos.Post> shown = chosen.stream().skip(from).limit(PAGE_SIZE).map(post -> reread(post, userId)).toList();
        return new NetworkDtos.SavedPosts(new NetworkDtos.Posts(shown, Math.max(0, page), chosen.size() > from + PAGE_SIZE), tags);
    }

    /**
     * Changes the text of the reader's own post within {@link #EDIT_WINDOW} of posting it, keeping the earlier text for its
     * readers; the post then reads as edited.
     */
    @Transactional
    public NetworkDtos.Post edit(Long userId, Long postId, NetworkDtos.NewPost request) {
        network.requireMember(userId);
        NetworkPost post = posts.findByIdAndAuthorId(postId, userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().plus(EDIT_WINDOW).isBefore(clock.instant())) {
            throw new InvalidStateException("A post can only be edited in the 15 minutes after it is posted");
        }
        String text = request.text().strip();
        if (!text.equals(post.getText())) {
            NetworkPostVersion version = new NetworkPostVersion();
            version.setPostId(postId);
            version.setText(post.getText());
            versions.save(version);
            post.setText(text);
            post.setTags(tags(text));
            post.setEditedAt(clock.instant());
        }
        return reread(post, userId);
    }

    /** The earlier texts of a post the reader can read, the latest first. */
    @Transactional(readOnly = true)
    public List<NetworkDtos.PostVersion> versions(Long userId, Long postId) {
        readable(userId, postId);
        return versions.findByPostIdOrderByCreatedAtDescIdDesc(postId).stream()
            .map(version -> new NetworkDtos.PostVersion(version.getText(), version.getCreatedAt())).toList();
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
        return reread(post, userId);
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
