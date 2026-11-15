package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkComment;
import dev.swirlit.indezy.model.NetworkCommentLike;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.repository.NetworkCommentLikeRepository;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comments under the members' posts (see docs/features.md#member-network): one level of replies, the oldest first and
 * the pinned one on top. A member deletes their own comment, its replies going with it; the post's author pins one,
 * hides one (then only they see it) or closes the comments. A member likes a comment they see, once.
 */
@Service
@RequiredArgsConstructor
public class NetworkCommentService {

    static final String NOT_FOUND = "No such comment";

    private final NetworkService network;
    private final NetworkPostService posts;
    private final NetworkPostRepository postRepository;
    private final NetworkCommentRepository comments;
    private final NetworkCommentLikeRepository likes;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkLimits limits;
    private final NetworkNotificationService notices;

    /** The comments the reader sees under a post they can read, each with its replies. */
    @Transactional(readOnly = true)
    public List<NetworkDtos.Comment> list(Long readerId, Long postId) {
        NetworkPost post = posts.readable(readerId, postId);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(readerId));
        Map<Long, Optional<NetworkDtos.MemberCard>> cards = new HashMap<>();
        List<NetworkComment> seen = comments.findByPostIdOrderByCreatedAtAscIdAsc(postId).stream()
            .filter(comment -> NetworkPostService.visible(post, comment, readerId, blocked))
            .filter(comment -> cards.computeIfAbsent(comment.getAuthorId(), posts::authorCard).isPresent()).toList();
        Map<Long, List<NetworkComment>> replies = seen.stream().filter(comment -> comment.getParentId() != null)
            .collect(Collectors.groupingBy(NetworkComment::getParentId));
        Likes liked = Likes.of(likes.findByPostId(postId), readerId, blocked);
        return seen.stream().filter(comment -> comment.getParentId() == null)
            .sorted(Comparator.comparing((NetworkComment comment) -> !comment.getId().equals(post.getPinnedCommentId())))
            .map(comment -> toDto(post, comment, readerId, cards, liked, replies.getOrDefault(comment.getId(), List.of()).stream()
                .map(reply -> toDto(post, reply, readerId, cards, liked, List.of())).toList()))
            .toList();
    }

    /** Comments a post the reader can read, or replies to one of its comments, unless its author closed the comments. */
    @Transactional
    public NetworkDtos.Comment comment(Long readerId, Long postId, NetworkDtos.NewComment request) {
        NetworkPost post = posts.readable(readerId, postId);
        if (post.isCommentsClosed()) {
            throw new InvalidStateException("Comments are closed on this post");
        }
        Optional<NetworkComment> parent = request.parentId() == null ? Optional.empty() : Optional.of(comments.findById(request.parentId())
            .filter(found -> found.getPostId().equals(postId) && found.getParentId() == null)
            .orElseThrow(() -> new IllegalArgumentException("A reply answers a comment under the same post")));
        limits.comment(readerId);
        NetworkComment comment = new NetworkComment();
        comment.setPostId(postId);
        comment.setAuthorId(readerId);
        comment.setParentId(request.parentId());
        comment.setText(request.text().strip());
        comments.save(comment);
        parent.ifPresent(answered -> notices.notify(answered.getAuthorId(), readerId, NetworkNotificationType.REPLY, postId));
        if (parent.map(answered -> !answered.getAuthorId().equals(post.getAuthorId())).orElse(true)) {
            notices.notify(post.getAuthorId(), readerId, NetworkNotificationType.COMMENT, postId);
        }
        notices.mentions(comment.getText(), readerId, postId);
        return toDto(post, comment, readerId, new HashMap<>(), Likes.NONE, List.of());
    }

    /** Likes a comment the reader sees, once, telling its writer; or no longer. */
    @Transactional
    public NetworkDtos.Comment like(Long readerId, Long commentId, boolean liked) {
        NetworkComment comment = comments.findById(commentId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        NetworkPost post = posts.readable(readerId, comment.getPostId());
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(readerId));
        if (!NetworkPostService.visible(post, comment, readerId, blocked) || posts.authorCard(comment.getAuthorId()).isEmpty()) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        Optional<NetworkCommentLike> existing = likes.findByCommentIdAndUserId(commentId, readerId);
        if (liked && existing.isEmpty()) {
            NetworkCommentLike like = new NetworkCommentLike();
            like.setCommentId(commentId);
            like.setPostId(comment.getPostId());
            like.setUserId(readerId);
            likes.save(like);
            notices.notify(comment.getAuthorId(), readerId, NetworkNotificationType.COMMENT_LIKE, comment.getPostId());
        } else if (!liked) {
            existing.ifPresent(likes::delete);
        }
        return toDto(post, comment, readerId, new HashMap<>(), Likes.of(likes.findByCommentId(commentId), readerId, blocked), List.of());
    }

    /** Deletes the reader's own comment, and its replies when it is under the post itself. */
    @Transactional
    public void delete(Long readerId, Long commentId) {
        network.requireMember(readerId);
        NetworkComment comment = comments.findById(commentId).filter(found -> found.getAuthorId().equals(readerId))
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        remove(comment);
    }

    /** Deletes the comment and its replies, unpinning it: by its author or the moderators. */
    void remove(NetworkComment comment) {
        List<NetworkComment> replies = comments.findByParentId(comment.getId());
        Set<Long> ids = new HashSet<>(Set.of(comment.getId()));
        replies.forEach(reply -> ids.add(reply.getId()));
        likes.deleteByCommentIdIn(ids);
        comments.deleteAll(replies);
        postRepository.findById(comment.getPostId()).filter(post -> comment.getId().equals(post.getPinnedCommentId()))
            .ifPresent(post -> post.setPinnedCommentId(null));
        comments.delete(comment);
    }

    /** Hides a comment under the reader's own post, or shows it again. */
    @Transactional
    public void hide(Long readerId, Long commentId, boolean hidden) {
        network.requireMember(readerId);
        NetworkComment comment = comments.findById(commentId)
            .filter(found -> postRepository.findByIdAndAuthorId(found.getPostId(), readerId).isPresent())
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        comment.setHidden(hidden);
    }

    /** Closes the comments of the reader's own post, or opens them again. */
    @Transactional
    public void close(Long readerId, Long postId, boolean closed) {
        network.requireMember(readerId);
        own(readerId, postId).setCommentsClosed(closed);
    }

    /** Pins a comment under the reader's own post above the others, or none. */
    @Transactional
    public void pin(Long readerId, Long postId, Long commentId) {
        network.requireMember(readerId);
        NetworkPost post = own(readerId, postId);
        if (commentId != null) {
            comments.findById(commentId).filter(comment -> comment.getPostId().equals(postId) && comment.getParentId() == null)
                .orElseThrow(() -> new IllegalArgumentException("Only a comment under the post itself can be pinned"));
        }
        post.setPinnedCommentId(commentId);
    }

    private NetworkPost own(Long readerId, Long postId) {
        return postRepository.findByIdAndAuthorId(postId, readerId)
            .orElseThrow(() -> new ResourceNotFoundException(NetworkPostService.NOT_FOUND));
    }

    private NetworkDtos.Comment toDto(NetworkPost post, NetworkComment comment, Long readerId, Map<Long, Optional<NetworkDtos.MemberCard>> cards,
                                      Likes liked, List<NetworkDtos.Comment> replies) {
        NetworkDtos.MemberCard author = cards.computeIfAbsent(comment.getAuthorId(), posts::authorCard).orElseThrow();
        return new NetworkDtos.Comment(comment.getId(), author, comment.getText(), comment.getCreatedAt(), comment.getAuthorId().equals(readerId),
            comment.isHidden(), Objects.equals(comment.getId(), post.getPinnedCommentId()), liked.counts().getOrDefault(comment.getId(), 0),
            liked.mine().contains(comment.getId()), replies);
    }

    /** How many likes each comment has, but those of members blocked either way, and which the reader gave. */
    private record Likes(Map<Long, Integer> counts, Set<Long> mine) {

        static final Likes NONE = new Likes(Map.of(), Set.of());

        static Likes of(List<NetworkCommentLike> likes, Long readerId, Set<Long> blocked) {
            Map<Long, Integer> counts = new HashMap<>();
            Set<Long> mine = new HashSet<>();
            for (NetworkCommentLike like : likes) {
                if (!blocked.contains(like.getUserId())) {
                    counts.merge(like.getCommentId(), 1, Integer::sum);
                }
                if (like.getUserId().equals(readerId)) {
                    mine.add(like.getCommentId());
                }
            }
            return new Likes(counts, mine);
        }
    }
}
