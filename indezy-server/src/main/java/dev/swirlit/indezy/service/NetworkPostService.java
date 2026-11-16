package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkBookmark;
import dev.swirlit.indezy.model.NetworkComment;
import dev.swirlit.indezy.model.NetworkCommentLike;
import dev.swirlit.indezy.model.NetworkLike;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkPostDetails;
import dev.swirlit.indezy.model.NetworkPostVersion;
import dev.swirlit.indezy.model.NetworkTagFollow;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import dev.swirlit.indezy.model.enums.NetworkReaction;
import dev.swirlit.indezy.repository.NetworkBookmarkRepository;
import dev.swirlit.indezy.repository.NetworkCommentLikeRepository;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkLikeRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkNotificationRepository;
import dev.swirlit.indezy.repository.NetworkMissionTakeRepository;
import dev.swirlit.indezy.repository.NetworkPollVoteRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkPostVersionRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.NetworkTagFollowRepository;
import dev.swirlit.indezy.repository.NetworkUsefulRepository;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final NetworkCommentLikeRepository commentLikes;
    private final NetworkCommentRepository comments;
    private final NetworkPostVersionRepository versions;
    private final NetworkBookmarkRepository bookmarks;
    private final NetworkTagFollowRepository tagFollows;
    private final Clock clock;
    private final NetworkLimits limits;
    private final NetworkSpamFilter spam;
    private final NetworkPolls polls;
    private final NetworkMissionTakes missionTakes;
    private final NetworkUsefuls usefuls;
    private final NetworkUsefulRepository usefulRepository;
    private final NetworkMissionTakeRepository takeRepository;
    private final NetworkPollVoteRepository pollVotes;
    private final NetworkNotificationService notices;
    private final NetworkNotificationRepository notificationRepository;
    private final NetworkLinkPreviews previews;

    /** Posts the text, unless the spam score holds it for review: then nothing is published yet. */
    @Transactional
    public Optional<NetworkDtos.Post> post(Long userId, NetworkDtos.NewPost request) {
        network.requireMember(userId);
        limits.post(userId);
        String text = request.text().strip();
        NetworkPostKind kind = request.kind() == null ? NetworkPostKind.POST : request.kind();
        NetworkPostDetails details = NetworkPostKinds.details(kind, request.details());
        NetworkDtos.NewPoll poll = kind == NetworkPostKind.POLL ? request.poll() : null;
        if (kind == NetworkPostKind.POLL && poll == null) {
            throw new IllegalArgumentException("A poll has two to four choices");
        }
        return spam.hold(userId, null, null, text) ? Optional.empty() : Optional.of(publish(userId, text, kind, details, poll));
    }

    /** Publishes the member's post: posted, or let go by an administrator once held (then as a plain post). */
    NetworkDtos.Post publish(Long userId, String text) {
        return publish(userId, text, NetworkPostKind.POST, null, null);
    }

    private NetworkDtos.Post publish(Long userId, String text, NetworkPostKind kind, NetworkPostDetails details, NetworkDtos.NewPoll poll) {
        NetworkMember author = network.requireMember(userId);
        NetworkPost post = new NetworkPost();
        post.setAuthorId(userId);
        post.setText(text);
        post.setKind(kind);
        post.setDetails(details);
        if (poll != null) {
            polls.open(post, poll);
        }
        post.setTags(tags(post.getText()));
        posts.save(post);
        previews.refresh(post);
        notices.mentions(post.getText(), userId, post.getId());
        log.info("Member {} posted {}", userId, post.getId());
        return toDto(post, network.card(author), userId, List.of(), new Seen(0, false, null, 0, polls.views(List.of(post), userId)
            .get(post.getId()), missionTakes.views(List.of(post), userId).get(post.getId()), new NetworkDtos.Useful(0, false)));
    }

    /** Deletes the reader's own post; another member's, like an unknown one, is not found. */
    @Transactional
    public void delete(Long userId, Long postId) {
        network.requireMember(userId);
        remove(posts.findByIdAndAuthorId(postId, userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND)));
    }

    /** Deletes the post with its likes, comments, versions, saves and notifications: by its author or the moderators. */
    void remove(NetworkPost post) {
        likes.deleteByPostId(post.getId());
        commentLikes.deleteByPostId(post.getId());
        pollVotes.deleteByPostId(post.getId());
        takeRepository.deleteByPostId(post.getId());
        usefulRepository.deleteByPostId(post.getId());
        comments.deleteByPostId(post.getId());
        versions.deleteByPostId(post.getId());
        bookmarks.deleteByPostId(post.getId());
        notificationRepository.deleteByPostId(post.getId());
        posts.delete(post);
    }

    /** A member's posts, for the members their page shows to; none of a private card to a member not following it. */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts memberPosts(Long viewerId, String handle, int page) {
        NetworkMember member = follows.visible(network.requireMember(viewerId), handle);
        if (follows.locked(viewerId).contains(member.getUserId())) {
            return new NetworkDtos.Posts(List.of(), Math.max(0, page), false);
        }
        return page(List.of(member.getUserId()), viewerId, page);
    }

    /** Every post of the account, the newest first, for its data export (without the author card: it is the account's). */
    @Transactional(readOnly = true)
    public List<NetworkDtos.Post> export(Long userId) {
        return posts.findByAuthorIdInOrderByCreatedAtDescIdDesc(List.of(userId), Pageable.unpaged()).stream()
            .map(post -> toDto(post, null, userId, List.of(), Seen.NOTHING)).toList();
    }

    /** What the account did on the network beyond its posts, for its data export. */
    @Transactional(readOnly = true)
    public NetworkDtos.Activity activity(Long userId) {
        return new NetworkDtos.Activity(
            comments.findByAuthorIdOrderByCreatedAtAsc(userId).stream()
                .map(comment -> new NetworkDtos.ExportedComment(comment.getPostId(), comment.getParentId(), comment.getText(), comment.getCreatedAt()))
                .toList(),
            likes.findByUserIdOrderByCreatedAtAsc(userId).stream().map(NetworkLike::getPostId).toList(),
            commentLikes.findByUserIdOrderByCreatedAtAsc(userId).stream().map(NetworkCommentLike::getCommentId).toList(),
            bookmarks.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(NetworkBookmark::getPostId).toList(),
            followRepository.following(userId).stream().map(NetworkMember::getHandle).toList(),
            tagFollows.findByUserIdOrderByTagAsc(userId).stream().map(NetworkTagFollow::getTag).toList());
    }

    private NetworkDtos.Posts page(Collection<Long> authors, Long readerId, int page) {
        return page(posts.findByAuthorIdInOrderByCreatedAtDescIdDesc(authors, PageRequest.of(Math.max(0, page), PAGE_SIZE)), readerId);
    }

    NetworkDtos.Posts page(Page<NetworkPost> found, Long readerId) {
        Map<Long, Optional<NetworkDtos.MemberCard>> cards = new HashMap<>();
        List<Long> ids = found.getContent().stream().map(NetworkPost::getId).toList();
        Map<Long, List<NetworkLike>> liked = likes.findByPostIdIn(ids).stream().collect(Collectors.groupingBy(NetworkLike::getPostId));
        Map<Long, List<NetworkComment>> commented = comments.findByPostIdIn(ids).stream()
            .collect(Collectors.groupingBy(NetworkComment::getPostId));
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(readerId));
        Set<Long> saved = bookmarks.findByUserIdAndPostIdIn(readerId, ids).stream().map(NetworkBookmark::getPostId).collect(Collectors.toSet());
        Set<Long> unseen = new HashSet<>(blocked);
        unseen.addAll(follows.locked(readerId));
        Map<Long, NetworkDtos.Original> originals = originals(found.getContent(), unseen);
        Map<Long, Integer> reposts = repostCounts(ids);
        Map<Long, NetworkDtos.Poll> pollViews = polls.views(found.getContent(), readerId);
        Map<Long, NetworkDtos.Taken> taken = missionTakes.views(found.getContent(), readerId);
        Map<Long, NetworkDtos.Useful> useful = usefuls.views(found.getContent(), readerId, blocked);
        List<NetworkDtos.Post> shown = found.getContent().stream()
            .map(post -> cards.computeIfAbsent(post.getAuthorId(), this::authorCard)
                .map(card -> toDto(post, card, readerId, liked.getOrDefault(post.getId(), List.of()),
                    new Seen(seenComments(post, readerId, blocked, commented.getOrDefault(post.getId(), List.of())), saved.contains(post.getId()),
                        originals.get(post.getId()), reposts.getOrDefault(post.getId(), 0), pollViews.get(post.getId()),
                        taken.get(post.getId()), useful.get(post.getId())))))
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

    /** What a post shows its reader beyond itself: the comments they see, whether they saved it, its original and reposts. */
    private record Seen(int comments, boolean bookmarked, NetworkDtos.Original original, int reposts, NetworkDtos.Poll poll,
                        NetworkDtos.Taken taken, NetworkDtos.Useful useful) {

        static final Seen NOTHING = new Seen(0, false, null, 0, null, null, null);
    }

    private static NetworkDtos.Post toDto(NetworkPost post, NetworkDtos.MemberCard author, Long readerId, List<NetworkLike> postLikes,
                                          Seen seen) {
        return new NetworkDtos.Post(post.getId(), author, post.getText(), List.copyOf(post.getTags()), post.getCreatedAt(),
            post.getAuthorId().equals(readerId), postLikes.size(), postLikes.stream().anyMatch(like -> like.getUserId().equals(readerId)),
            seen.comments(), post.isCommentsClosed(), post.getEditedAt() != null, seen.bookmarked(), seen.original(), seen.reposts(),
            post.isRepostsOff(), post.getKind(), NetworkPostKinds.toDto(post.getDetails()), post.getAnswerCommentId(), seen.poll(),
            seen.taken(), seen.useful(), reactions(postLikes), postLikes.stream().filter(like -> like.getUserId().equals(readerId))
            .map(NetworkLike::reactionOrLike).findFirst().orElse(null), NetworkLinkPreviews.view(post.getPreview()));
    }


    /** How many members reacted each way, the most frequent first. */
    private static List<NetworkDtos.ReactionCount> reactions(List<NetworkLike> postLikes) {
        Map<NetworkReaction, Long> counted = postLikes.stream()
            .collect(Collectors.groupingBy(NetworkLike::reactionOrLike, () -> new EnumMap<>(NetworkReaction.class), Collectors.counting()));
        return counted.entrySet().stream().sorted(Map.Entry.<NetworkReaction, Long>comparingByValue().reversed())
            .map(entry -> new NetworkDtos.ReactionCount(entry.getKey(), entry.getValue().intValue())).toList();
    }

    /** The originals of the reposts among these posts, as the reader can read them, by the reposts' ids. */
    private Map<Long, NetworkDtos.Original> originals(List<NetworkPost> shown, Set<Long> blocked) {
        Set<Long> ids = shown.stream().map(NetworkPost::getRepostOfId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, NetworkPost> found = posts.findAllById(ids).stream().collect(Collectors.toMap(NetworkPost::getId, post -> post));
        Map<Long, NetworkDtos.Original> originals = new HashMap<>();
        for (NetworkPost post : shown) {
            if (post.getRepostOfId() != null) {
                NetworkPost original = found.get(post.getRepostOfId());
                Optional<NetworkDtos.MemberCard> author = original == null || blocked.contains(original.getAuthorId()) ? Optional.empty() :
                    authorCard(original.getAuthorId());
                originals.put(post.getId(), author.map(card -> new NetworkDtos.Original(original.getId(), card, original.getText(),
                    original.getCreatedAt(), true)).orElseGet(() -> new NetworkDtos.Original(post.getRepostOfId(), null, null, null, false)));
            }
        }
        return originals;
    }

    /** How many reposts each of these posts has. */
    private Map<Long, Integer> repostCounts(Collection<Long> ids) {
        return posts.findByRepostOfIdIn(ids).stream().collect(Collectors.groupingBy(NetworkPost::getRepostOfId, Collectors.summingInt(post -> 1)));
    }

    /**
     * Reposts a post the reader can read, with an optional comment; a repost of a repost reposts its original. Refused
     * when the original's author turned reposts off.
     */
    @Transactional
    public NetworkDtos.Post repost(Long userId, Long postId, NetworkDtos.NewRepost request) {
        NetworkPost read = readable(userId, postId);
        NetworkPost original = read.getRepostOfId() == null ? read : readable(userId, read.getRepostOfId());
        if (original.isRepostsOff()) {
            throw new InvalidStateException("Its author turned reposts off for this post");
        }
        limits.post(userId);
        NetworkPost repost = new NetworkPost();
        repost.setAuthorId(userId);
        repost.setRepostOfId(original.getId());
        repost.setText(request.text() == null ? "" : request.text().strip());
        repost.setTags(tags(repost.getText()));
        posts.save(repost);
        notices.notify(original.getAuthorId(), userId, NetworkNotificationType.REPOST, original.getId());
        notices.mentions(repost.getText(), userId, repost.getId());
        return reread(repost, userId);
    }

    /** Turns reposts of the reader's own post off, or on again. */
    @Transactional
    public NetworkDtos.Post repostsOff(Long userId, Long postId, boolean off) {
        network.requireMember(userId);
        NetworkPost post = posts.findByIdAndAuthorId(postId, userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        post.setRepostsOff(off);
        return reread(post, userId);
    }

    /** A single post as the reader now reads it. */
    NetworkDtos.Post reread(NetworkPost post, Long readerId) {
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(readerId));
        return toDto(post, authorCard(post.getAuthorId()).orElseThrow(), readerId, likes.findByPostIdIn(List.of(post.getId())),
            new Seen(seenComments(post, readerId, blocked, comments.findByPostIdIn(List.of(post.getId()))),
                bookmarks.findByUserIdAndPostId(readerId, post.getId()).isPresent(), originals(List.of(post), blocked).get(post.getId()),
                repostCounts(List.of(post.getId())).getOrDefault(post.getId(), 0), polls.views(List.of(post), readerId).get(post.getId()),
                missionTakes.views(List.of(post), readerId).get(post.getId()), usefuls.views(List.of(post), readerId, blocked)
                .get(post.getId())));
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
        Set<Long> blocked = new HashSet<>(restrictions.blockedEitherWay(userId));
        blocked.addAll(follows.locked(userId));
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
            previews.refresh(post);
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
    public NetworkDtos.Post like(Long userId, Long postId, NetworkReaction reaction) {
        NetworkPost post = readable(userId, postId);
        Optional<NetworkLike> existing = likes.findByPostIdAndUserId(postId, userId);
        if (reaction != null && existing.isEmpty()) {
            NetworkLike like = new NetworkLike();
            like.setPostId(postId);
            like.setUserId(userId);
            like.setReaction(reaction);
            likes.save(like);
            notices.notify(post.getAuthorId(), userId, NetworkNotificationType.LIKE, postId);
        } else if (reaction != null) {
            existing.get().setReaction(reaction);
        } else {
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

    /** One post as its reader sees it, for its own page; not found when they cannot read it. */
    @Transactional(readOnly = true)
    public NetworkDtos.Post one(Long userId, Long postId) {
        return reread(readable(userId, postId), userId);
    }

    /** A post the reader can read: its author in the network, neither blocking the other, nor private to them; not found otherwise. */
    NetworkPost readable(Long userId, Long postId) {
        network.requireMember(userId);
        NetworkPost post = posts.findById(postId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (authorCard(post.getAuthorId()).isEmpty() || restrictions.blockedEitherWay(userId).contains(post.getAuthorId()) ||
            follows.locked(userId).contains(post.getAuthorId())) {
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
