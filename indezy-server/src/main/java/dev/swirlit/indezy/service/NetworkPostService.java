package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkRestrictionKind;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
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
    static final int MAX_TAGS = 10;
    /** A #tag: a letter or digit, then up to 39 letters, digits, dashes or underscores, not inside a word. */
    private static final Pattern TAG = Pattern.compile("(?<![\\p{L}\\p{N}_#])#([\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,39})");

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final NetworkPostRepository posts;
    private final NetworkMemberRepository members;
    private final NetworkFollowRepository followRepository;
    private final NetworkRestrictionRepository restrictions;

    @Transactional
    public NetworkDtos.Post post(Long userId, NetworkDtos.NewPost request) {
        NetworkMember author = network.requireMember(userId);
        NetworkPost post = new NetworkPost();
        post.setAuthorId(userId);
        post.setText(request.text().strip());
        post.setTags(tags(post.getText()));
        posts.save(post);
        log.info("Member {} posted {}", userId, post.getId());
        return toDto(post, network.card(author), userId);
    }

    /** Deletes the reader's own post; another member's, like an unknown one, is not found. */
    @Transactional
    public void delete(Long userId, Long postId) {
        network.requireMember(userId);
        posts.delete(posts.findByIdAndAuthorId(postId, userId).orElseThrow(() -> new ResourceNotFoundException("No such post")));
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
            .map(post -> toDto(post, null, userId)).toList();
    }

    private NetworkDtos.Posts page(Collection<Long> authors, Long readerId, int page) {
        Page<NetworkPost> found = posts.findByAuthorIdInOrderByCreatedAtDescIdDesc(authors, PageRequest.of(Math.max(0, page), PAGE_SIZE));
        Map<Long, Optional<NetworkDtos.MemberCard>> cards = new HashMap<>();
        List<NetworkDtos.Post> shown = found.getContent().stream()
            .map(post -> cards.computeIfAbsent(post.getAuthorId(), this::authorCard).map(card -> toDto(post, card, readerId)))
            .flatMap(Optional::stream).toList();
        return new NetworkDtos.Posts(shown, found.getNumber(), found.hasNext());
    }

    /** The author's card while they are in the network; their posts are hidden while they are away. */
    private Optional<NetworkDtos.MemberCard> authorCard(Long authorId) {
        return members.findByUserId(authorId).filter(NetworkMember::isActive).flatMap(network::liveCard);
    }

    private static NetworkDtos.Post toDto(NetworkPost post, NetworkDtos.MemberCard author, Long readerId) {
        return new NetworkDtos.Post(post.getId(), author, post.getText(), List.copyOf(post.getTags()), post.getCreatedAt(),
            post.getAuthorId().equals(readerId));
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
