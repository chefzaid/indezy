package dev.swirlit.indezy.service;

import dev.swirlit.indezy.admin.AdminService;
import dev.swirlit.indezy.dto.NetworkCommunityDtos;
import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkCommunity;
import dev.swirlit.indezy.model.NetworkCommunityMember;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.repository.NetworkCommunityMemberRepository;
import dev.swirlit.indezy.repository.NetworkCommunityRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The member network's communities (see docs/features.md#member-network): any member creates one, joined at will, with its
 * own feed; its creator moderates it and names co-moderators, who remove posts and members; the administrators moderate
 * every community, keeping the last word (decided on 2026-11-20, TODO.md **Member network audience**).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkCommunityService {

    static final String NOT_FOUND = "No such community";
    /** How many communities a member creates at most. */
    static final int CREATED_MAX = 10;

    private final NetworkService network;
    private final NetworkPostService postService;
    private final NetworkFeedService feed;
    private final NetworkCommunityRepository communities;
    private final NetworkCommunityMemberRepository memberships;
    private final NetworkMemberRepository members;
    private final NetworkPostRepository posts;
    private final AdminService admin;
    private final NetworkNotificationService notices;

    /** The communities, those the reader is in first, then the largest; those whose name holds what was typed when asked. */
    @Transactional(readOnly = true)
    public List<NetworkCommunityDtos.Community> list(Long userId, String typed) {
        network.requireMember(userId);
        String query = Optional.ofNullable(NetworkPost.fold(typed)).orElse("");
        Map<Long, Integer> sizes = sizes();
        Map<Long, NetworkCommunityMember> mine = memberships.findByUserIdAndRemovedFalse(userId).stream()
            .collect(Collectors.toMap(NetworkCommunityMember::getCommunityId, membership -> membership));
        return communities.findAllByOrderByNameAsc().stream()
            .filter(community -> query.isEmpty() || NetworkPost.fold(community.getName()).contains(query) || community.getSlug().contains(query))
            .map(community -> view(community, sizes.getOrDefault(community.getId(), 0), mine.get(community.getId())))
            .sorted(Comparator.comparing((NetworkCommunityDtos.Community community) -> !community.joined())
                .thenComparing(Comparator.comparingInt(NetworkCommunityDtos.Community::members).reversed()))
            .toList();
    }

    /** Creates a community, its creator in it as its moderator. */
    @Transactional
    public NetworkCommunityDtos.Community create(Long userId, NetworkCommunityDtos.NewCommunity request) {
        network.requireMember(userId);
        if (communities.countByCreatorId(userId) >= CREATED_MAX) {
            throw new InvalidStateException("A member creates " + CREATED_MAX + " communities at most");
        }
        NetworkCommunity community = new NetworkCommunity();
        community.setName(request.name().strip());
        community.setDescription(blankToNull(request.description()));
        community.setSlug(freeSlug(community.getName()));
        community.setPrivateCommunity(Boolean.TRUE.equals(request.privateCommunity()));
        community.setCreatorId(userId);
        communities.save(community);
        NetworkCommunityMember creator = membership(community.getId(), userId);
        creator.setModerator(true);
        memberships.save(creator);
        log.info("Member {} created community {}", userId, community.getId());
        return view(community, 1, creator);
    }

    /** A community's page: the community, its moderators, and whether the reader created it (or administers the network). */
    @Transactional(readOnly = true)
    public NetworkCommunityDtos.Page page(Long userId, String slug) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        List<NetworkCommunityMember> in = in(community.getId());
        NetworkCommunityMember mine = memberships.findByCommunityIdAndUserId(community.getId(), userId).orElse(null);
        List<NetworkDtos.MemberCard> moderators = in.stream().filter(NetworkCommunityMember::isModerator)
            .flatMap(membership -> members.findByUserId(membership.getUserId()).filter(NetworkMember::isActive).flatMap(network::liveCard).stream())
            .toList();
        NetworkCommunityDtos.Community view = view(community, in.size(), mine);
        boolean administrator = admin.isAdmin(userId);
        // The administrators have the creator's hand in every community: the last word.
        return new NetworkCommunityDtos.Page(new NetworkCommunityDtos.Community(view.slug(), view.name(), view.description(), view.members(),
            view.joined(), view.moderator() || administrator, view.privateCommunity(), view.requested()), moderators,
            community.getCreatorId().equals(userId) || administrator);
    }

    /** Changes a community's name and description: its moderators. */
    @Transactional
    public NetworkCommunityDtos.Page update(Long userId, String slug, NetworkCommunityDtos.NewCommunity request) {
        NetworkCommunity community = moderated(userId, slug);
        community.setName(request.name().strip());
        community.setDescription(blankToNull(request.description()));
        if (request.privateCommunity() != null) {
            community.setPrivateCommunity(request.privateCommunity());
            if (!request.privateCommunity()) {
                // Opened: every request waiting is let in.
                pending(community.getId()).forEach(waiting -> accept(userId, community, waiting));
            }
        }
        communities.save(community);
        return page(userId, slug);
    }

    /** Joins a community, or asks to join a private one; one its moderators removed the member from refuses them (409). */
    @Transactional
    public NetworkCommunityDtos.Page join(Long userId, String slug) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        Optional<NetworkCommunityMember> known = memberships.findByCommunityIdAndUserId(community.getId(), userId);
        if (known.filter(NetworkCommunityMember::isRemoved).isPresent()) {
            throw new InvalidStateException("The moderators removed this member from the community");
        }
        if (known.isEmpty()) {
            NetworkCommunityMember joining = membership(community.getId(), userId);
            if (community.isPrivate()) {
                // A private community: a request, told to its moderators.
                joining.setPending(true);
                in(community.getId()).stream().filter(NetworkCommunityMember::isModerator).forEach(moderator ->
                    notices.notify(moderator.getUserId(), userId, NetworkNotificationType.COMMUNITY_REQUEST, null, community.getId()));
            }
            memberships.save(joining);
        }
        return page(userId, slug);
    }

    /** Leaves a community, or withdraws a request to join it; when its last moderator leaves, its earliest member moderates it. */
    @Transactional
    public NetworkCommunityDtos.Page leave(Long userId, String slug) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        memberships.findByCommunityIdAndUserId(community.getId(), userId).filter(membership -> !membership.isRemoved())
            .ifPresent(memberships::delete);
        memberships.flush();
        List<NetworkCommunityMember> left = in(community.getId());
        if (!left.isEmpty() && left.stream().noneMatch(NetworkCommunityMember::isModerator)) {
            left.getFirst().setModerator(true);
            memberships.save(left.getFirst());
        }
        return page(userId, slug);
    }

    /**
     * A community's posts, the newest first, 20 a page: an open community's to every member, a private one's to its members
     * (and the administrators), those muted or blocked either way aside.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.Posts posts(Long userId, String slug, int page) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        List<Long> excluded = new ArrayList<>(feed.excluded(userId));
        if (excluded.isEmpty()) {
            excluded.add(-1L);
        }
        return postService.page(posts.inCommunity(community.getId(), excluded, PageRequest.of(Math.max(0, page), NetworkPostService.PAGE_SIZE)),
            userId);
    }

    /** Posts in a community, for its members; none when the spam score holds it (then published, if let go, as a plain post). */
    @Transactional
    public Optional<NetworkDtos.Post> post(Long userId, String slug, NetworkCommunityDtos.NewCommunityPost request) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        memberships.findByCommunityIdAndUserId(community.getId(), userId).filter(membership -> !membership.isRemoved() && !membership.isPending())
            .orElseThrow(() -> new AccessDeniedException("Only its members post in a community"));
        Optional<NetworkDtos.Post> posted = postService.post(userId, new NetworkDtos.NewPost(request.text()));
        posted.flatMap(post -> posts.findById(post.id())).ifPresent(post -> {
            post.setCommunityId(community.getId());
            posts.save(post);
            // Its members are told, as they choose in Profile → Network.
            in(community.getId()).forEach(member ->
                notices.notify(member.getUserId(), userId, NetworkNotificationType.COMMUNITY_POST, post.getId(), community.getId()));
        });
        return posted.map(post -> postService.one(userId, post.id()));
    }

    /** The members of a community, its moderators first. */
    @Transactional(readOnly = true)
    public List<NetworkCommunityDtos.CommunityMember> members(Long userId, String slug) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        Set<Long> blocked = Set.copyOf(feed.excluded(userId));
        return in(community.getId()).stream()
            .filter(membership -> !blocked.contains(membership.getUserId()))
            .sorted(Comparator.comparing((NetworkCommunityMember membership) -> !membership.isModerator()))
            .flatMap(membership -> members.findByUserId(membership.getUserId()).filter(NetworkMember::isActive).flatMap(network::liveCard)
                .map(card -> new NetworkCommunityDtos.CommunityMember(card, membership.isModerator())).stream())
            .toList();
    }

    /** Removes a post from a community, deleting it: its moderators or the administrators. */
    @Transactional
    public void removePost(Long userId, String slug, Long postId) {
        NetworkCommunity community = moderated(userId, slug);
        NetworkPost post = posts.findById(postId).filter(found -> community.getId().equals(found.getCommunityId()))
            .orElseThrow(() -> new ResourceNotFoundException(NetworkPostService.NOT_FOUND));
        postService.remove(post);
        log.info("Member {} removed post {} from community {}", userId, postId, community.getId());
    }

    /** Removes a member from a community, who cannot join it again; never its creator, and a moderator only by the creator. */
    @Transactional
    public void removeMember(Long userId, String slug, String handle) {
        NetworkCommunity community = moderated(userId, slug);
        NetworkCommunityMember target = target(community, handle);
        boolean creatorOrAdmin = community.getCreatorId().equals(userId) || admin.isAdmin(userId);
        if (target.getUserId().equals(community.getCreatorId()) || target.isModerator() && !creatorOrAdmin) {
            throw new AccessDeniedException("This member cannot be removed by this moderator");
        }
        target.setRemoved(true);
        target.setModerator(false);
        memberships.save(target);
    }

    /** Names a member a co-moderator, or no longer: the creator or the administrators. */
    @Transactional
    public List<NetworkCommunityDtos.CommunityMember> setModerator(Long userId, String slug, String handle, boolean moderator) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        if (!community.getCreatorId().equals(userId) && !admin.isAdmin(userId)) {
            throw new AccessDeniedException("Only its creator names a community's moderators");
        }
        NetworkCommunityMember target = target(community, handle);
        if (target.getUserId().equals(community.getCreatorId())) {
            throw new InvalidStateException("The creator moderates the community");
        }
        target.setModerator(moderator);
        memberships.save(target);
        if (moderator) {
            notices.notify(target.getUserId(), userId, NetworkNotificationType.COMMUNITY_MODERATOR, null, community.getId());
        }
        return members(userId, slug);
    }

    /** Deletes a community: its creator or the administrators; its posts stay, as plain posts, and its members go. */
    @Transactional
    public void delete(Long userId, String slug) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        if (!community.getCreatorId().equals(userId) && !admin.isAdmin(userId)) {
            throw new AccessDeniedException("Only its creator deletes a community");
        }
        posts.clearCommunity(community.getId());
        memberships.deleteByCommunityId(community.getId());
        communities.delete(community);
        log.info("Member {} deleted community {}", userId, community.getId());
    }

    private NetworkCommunity community(String slug) {
        return communities.findBySlug(Optional.ofNullable(slug).orElse("")).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** The community, when the reader moderates it (or is an administrator); otherwise refused. */
    private NetworkCommunity moderated(Long userId, String slug) {
        network.requireMember(userId);
        NetworkCommunity community = community(slug);
        boolean moderator = memberships.findByCommunityIdAndUserId(community.getId(), userId)
            .filter(membership -> membership.isModerator() && !membership.isRemoved()).isPresent();
        if (!moderator && !admin.isAdmin(userId)) {
            throw new AccessDeniedException("Only its moderators moderate a community");
        }
        return community;
    }

    private NetworkCommunityMember target(NetworkCommunity community, String handle) {
        NetworkMember member = members.findByHandle(Optional.ofNullable(handle).orElse(""))
            .orElseThrow(() -> new ResourceNotFoundException(NetworkService.NOT_FOUND));
        return memberships.findByCommunityIdAndUserId(community.getId(), member.getUserId())
            .filter(membership -> !membership.isRemoved() && !membership.isPending())
            .orElseThrow(() -> new ResourceNotFoundException(NetworkService.NOT_FOUND));
    }

    /** The members in a community: neither removed nor waiting for an answer, the earliest first. */
    private List<NetworkCommunityMember> in(Long communityId) {
        return memberships.findByCommunityIdAndRemovedFalseOrderByCreatedAtAsc(communityId).stream().filter(membership -> !membership.isPending())
            .toList();
    }

    /** The requests to join a community waiting for an answer, the earliest first. */
    private List<NetworkCommunityMember> pending(Long communityId) {
        return memberships.findByCommunityIdAndRemovedFalseOrderByCreatedAtAsc(communityId).stream().filter(NetworkCommunityMember::isPending)
            .toList();
    }

    private void accept(Long moderatorId, NetworkCommunity community, NetworkCommunityMember waiting) {
        waiting.setPending(false);
        memberships.save(waiting);
        notices.notify(waiting.getUserId(), moderatorId, NetworkNotificationType.COMMUNITY_ACCEPTED, null, community.getId());
    }

    /** The members waiting to join a private community: its moderators. */
    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> requests(Long userId, String slug) {
        NetworkCommunity community = moderated(userId, slug);
        return pending(community.getId()).stream()
            .flatMap(waiting -> members.findByUserId(waiting.getUserId()).filter(NetworkMember::isActive).flatMap(network::liveCard).stream())
            .toList();
    }

    /** Lets a member into a private community, who is told, or declines (untold): its moderators. */
    @Transactional
    public List<NetworkDtos.MemberCard> answer(Long userId, String slug, String handle, boolean accepted) {
        NetworkCommunity community = moderated(userId, slug);
        NetworkMember member = members.findByHandle(Optional.ofNullable(handle).orElse(""))
            .orElseThrow(() -> new ResourceNotFoundException(NetworkService.NOT_FOUND));
        NetworkCommunityMember waiting = memberships.findByCommunityIdAndUserId(community.getId(), member.getUserId())
            .filter(NetworkCommunityMember::isPending).orElseThrow(() -> new ResourceNotFoundException(NetworkService.NOT_FOUND));
        if (accepted) {
            accept(userId, community, waiting);
        } else {
            memberships.delete(waiting);
        }
        return requests(userId, slug);
    }

    private NetworkCommunityMember membership(Long communityId, Long userId) {
        NetworkCommunityMember membership = new NetworkCommunityMember();
        membership.setCommunityId(communityId);
        membership.setUserId(userId);
        return membership;
    }

    private Map<Long, Integer> sizes() {
        Map<Long, Integer> sizes = new HashMap<>();
        memberships.sizes().forEach(row -> sizes.put((Long) row[0], ((Number) row[1]).intValue()));
        return sizes;
    }

    private static NetworkCommunityDtos.Community view(NetworkCommunity community, int size, NetworkCommunityMember mine) {
        boolean joined = mine != null && !mine.isRemoved() && !mine.isPending();
        return new NetworkCommunityDtos.Community(community.getSlug(), community.getName(), community.getDescription(), size, joined,
            joined && mine.isModerator(), community.isPrivate(), mine != null && mine.isPending());
    }

    /** A slug from the name ("Kafka à Lyon" reads "kafka-a-lyon"), made unique with a number when taken. */
    private String freeSlug(String name) {
        String base = Optional.ofNullable(NetworkPost.fold(name)).orElse("").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        base = base.isEmpty() ? "communaute" : base.substring(0, Math.min(40, base.length()));
        String slug = base;
        for (int suffix = 2; communities.existsBySlug(slug); suffix++) {
            slug = base + "-" + suffix;
        }
        return slug;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
