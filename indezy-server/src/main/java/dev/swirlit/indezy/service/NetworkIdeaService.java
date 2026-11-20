package dev.swirlit.indezy.service;

import dev.swirlit.indezy.admin.AdminService;
import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.NetworkIdeaDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkIdea;
import dev.swirlit.indezy.model.NetworkIdeaVote;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkIdeaStatus;
import dev.swirlit.indezy.repository.NetworkIdeaRepository;
import dev.swirlit.indezy.repository.NetworkIdeaVoteRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The ideas board of the member network (see docs/features.md#member-network): members propose ideas for Indezy and vote
 * for those they want, once each; the administrators answer with a status (planned, in progress, done, declined) and a
 * word, and merge duplicates, moving their votes; those who voted are told when an idea moves on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkIdeaService {

    static final String NOT_FOUND = "No such idea";
    static final int PAGE_SIZE = 20;
    /** How many ideas a member proposes a day at most. */
    static final int DAILY_MAX = 5;
    /** The statuses an idea is still voted for in: answered as done or declined, or merged, it takes no more votes. */
    private static final Set<NetworkIdeaStatus> VOTED_FOR = EnumSet.of(NetworkIdeaStatus.OPEN, NetworkIdeaStatus.PLANNED, NetworkIdeaStatus.IN_PROGRESS);

    /** How the board is ordered: the most voted first, or the newest. */
    public enum Order { TOP, NEW }

    private final NetworkService network;
    private final NetworkIdeaRepository ideas;
    private final NetworkIdeaVoteRepository votes;
    private final NetworkMemberRepository members;
    private final NetworkNotificationRepository notifications;
    private final NetworkNotificationService notices;
    private final AdminService admin;
    private final Clock clock;

    /** A page of the board: the ideas of a status (all but the merged when none), those whose words hold what was typed. */
    @Transactional(readOnly = true)
    public NetworkIdeaDtos.Ideas list(Long userId, Order order, NetworkIdeaStatus status, String typed, int page) {
        network.requireMember(userId);
        String query = Optional.ofNullable(NetworkPost.fold(typed)).orElse("").strip();
        Map<Long, Long> counts = counts();
        Comparator<NetworkIdea> newest = Comparator.comparing(NetworkIdea::getCreatedAt).thenComparing(NetworkIdea::getId).reversed();
        Comparator<NetworkIdea> sort = order == Order.NEW ? newest :
            Comparator.comparingLong((NetworkIdea idea) -> counts.getOrDefault(idea.getId(), 0L)).reversed().thenComparing(newest);
        List<NetworkIdea> found = ideas.findTop2000ByStatusNotOrderByCreatedAtDescIdDesc(NetworkIdeaStatus.MERGED).stream()
            .filter(idea -> status == null || idea.getStatus() == status)
            .filter(idea -> query.isEmpty() || NetworkPost.fold(idea.getTitle() + " " + Optional.ofNullable(idea.getDescription()).orElse("")).contains(query))
            .sorted(sort).toList();
        int from = Math.min(Math.max(page, 0) * PAGE_SIZE, found.size());
        int to = Math.min(from + PAGE_SIZE, found.size());
        Set<Long> mine = new HashSet<>(votes.ideasOf(userId));
        List<NetworkIdeaDtos.Idea> shown = found.subList(from, to).stream()
            .map(idea -> view(idea, counts.getOrDefault(idea.getId(), 0L), mine.contains(idea.getId()), userId)).toList();
        return new NetworkIdeaDtos.Ideas(shown, Math.max(page, 0), to < found.size(), admin.isAdmin(userId));
    }

    /** One idea, a merged one naming the idea it was merged into. */
    @Transactional(readOnly = true)
    public NetworkIdeaDtos.Idea get(Long userId, Long id) {
        network.requireMember(userId);
        return view(userId, idea(id));
    }

    /** Proposes an idea, its author's vote counted; {@value #DAILY_MAX} a day at most. */
    @Transactional
    public NetworkIdeaDtos.Idea create(Long userId, NetworkIdeaDtos.NewIdea request) {
        network.requireMember(userId);
        if (ideas.countByAuthorIdAndCreatedAtAfter(userId, LocalDateTime.now(clock).minusDays(1)) >= DAILY_MAX) {
            throw new InvalidStateException("A member proposes " + DAILY_MAX + " ideas a day at most");
        }
        NetworkIdea idea = new NetworkIdea();
        idea.setAuthorId(userId);
        idea.setTitle(request.title().strip());
        idea.setDescription(blankToNull(request.description()));
        idea.setStatus(NetworkIdeaStatus.OPEN);
        ideas.save(idea);
        vote(idea.getId(), userId);
        log.info("Member {} proposed idea {}", userId, idea.getId());
        return view(userId, idea);
    }

    /** Changes an idea's title and description: its author, while it is open. */
    @Transactional
    public NetworkIdeaDtos.Idea update(Long userId, Long id, NetworkIdeaDtos.NewIdea request) {
        network.requireMember(userId);
        NetworkIdea idea = idea(id);
        if (!userId.equals(idea.getAuthorId())) {
            throw new AccessDeniedException("Only its author changes an idea");
        }
        if (idea.getStatus() != NetworkIdeaStatus.OPEN) {
            throw new InvalidStateException("An answered idea is not changed");
        }
        idea.setTitle(request.title().strip());
        idea.setDescription(blankToNull(request.description()));
        ideas.save(idea);
        return view(userId, idea);
    }

    /** Deletes an idea: its author while it is open and no one else voted for it, or the administrators. */
    @Transactional
    public void delete(Long userId, Long id) {
        network.requireMember(userId);
        NetworkIdea idea = idea(id);
        boolean administrator = admin.isAdmin(userId);
        if (!administrator && !userId.equals(idea.getAuthorId())) {
            throw new AccessDeniedException("Only its author or the administrators delete an idea");
        }
        if (!administrator && (idea.getStatus() != NetworkIdeaStatus.OPEN || votes.findByIdeaId(id).stream().anyMatch(vote -> !vote.getUserId().equals(userId)))) {
            throw new InvalidStateException("An idea others voted for, or answered, stays");
        }
        remove(idea);
    }

    /** Votes for an idea, or takes the vote back; one done, declined or merged takes no more votes. */
    @Transactional
    public NetworkIdeaDtos.Idea vote(Long userId, Long id, boolean on) {
        network.requireMember(userId);
        NetworkIdea idea = idea(id);
        Optional<NetworkIdeaVote> known = votes.findByIdeaIdAndUserId(id, userId);
        if (on && known.isEmpty()) {
            if (!VOTED_FOR.contains(idea.getStatus())) {
                throw new InvalidStateException("This idea takes no more votes");
            }
            vote(id, userId);
        } else if (!on) {
            known.ifPresent(votes::delete);
        }
        return view(userId, idea);
    }

    /** Answers an idea with a status and a word: the administrators; those who voted for it are told when its status changes. */
    @Transactional
    public NetworkIdeaDtos.Idea status(Long userId, Long id, NetworkIdeaDtos.StatusChange change) {
        network.requireMember(userId);
        requireAdmin(userId);
        if (change.status() == NetworkIdeaStatus.MERGED) {
            throw new InvalidStateException("Merging an idea sets it merged");
        }
        NetworkIdea idea = idea(id);
        if (idea.getStatus() == NetworkIdeaStatus.MERGED) {
            throw new InvalidStateException("A merged idea is answered through the idea it was merged into");
        }
        boolean moved = idea.getStatus() != change.status();
        idea.setStatus(change.status());
        idea.setStatusNote(blankToNull(change.note()));
        if (moved) {
            idea.setStatusChangedAt(LocalDateTime.now(clock));
        }
        ideas.save(idea);
        if (moved) {
            votes.findByIdeaId(id).forEach(vote -> notices.idea(vote.getUserId(), id));
        }
        return view(userId, idea);
    }

    /** Merges a duplicate into the idea it repeats, moving its votes there: the administrators; its voters are told. */
    @Transactional
    public NetworkIdeaDtos.Idea merge(Long userId, Long id, Long intoId) {
        network.requireMember(userId);
        requireAdmin(userId);
        if (id.equals(intoId)) {
            throw new InvalidStateException("An idea is not merged into itself");
        }
        NetworkIdea duplicate = idea(id);
        NetworkIdea kept = idea(intoId);
        if (duplicate.getStatus() == NetworkIdeaStatus.MERGED || kept.getStatus() == NetworkIdeaStatus.MERGED) {
            throw new InvalidStateException("A merged idea is not merged again, nor merged into");
        }
        Set<Long> already = new HashSet<>(votes.findByIdeaId(intoId).stream().map(NetworkIdeaVote::getUserId).toList());
        for (NetworkIdeaVote vote : votes.findByIdeaId(id)) {
            if (already.add(vote.getUserId())) {
                vote(intoId, vote.getUserId());
                notices.idea(vote.getUserId(), intoId);
            }
        }
        votes.deleteByIdeaId(id);
        duplicate.setStatus(NetworkIdeaStatus.MERGED);
        duplicate.setMergedIntoId(intoId);
        duplicate.setStatusChangedAt(LocalDateTime.now(clock));
        ideas.save(duplicate);
        log.info("Administrator {} merged idea {} into {}", userId, id, intoId);
        return view(userId, kept);
    }

    private void requireAdmin(Long userId) {
        if (!admin.isAdmin(userId)) {
            throw new AccessDeniedException("Only the administrators answer ideas");
        }
    }

    private void remove(NetworkIdea idea) {
        votes.deleteByIdeaId(idea.getId());
        notifications.deleteByIdeaId(idea.getId());
        ideas.delete(idea);
    }

    private void vote(Long ideaId, Long userId) {
        NetworkIdeaVote vote = new NetworkIdeaVote();
        vote.setIdeaId(ideaId);
        vote.setUserId(userId);
        votes.save(vote);
    }

    private NetworkIdea idea(Long id) {
        return ideas.findById(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private Map<Long, Long> counts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : votes.counts()) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private NetworkIdeaDtos.Idea view(Long userId, NetworkIdea idea) {
        return view(idea, votes.countByIdeaId(idea.getId()), votes.findByIdeaIdAndUserId(idea.getId(), userId).isPresent(), userId);
    }

    private NetworkIdeaDtos.Idea view(NetworkIdea idea, long count, boolean voted, Long userId) {
        NetworkDtos.MemberCard author = idea.getAuthorId() == null ? null :
            members.findByUserId(idea.getAuthorId()).filter(NetworkMember::isActive).flatMap(network::liveCard).orElse(null);
        NetworkDtos.IdeaRef into = idea.getMergedIntoId() == null ? null : ideas.findById(idea.getMergedIntoId())
            .map(found -> new NetworkDtos.IdeaRef(found.getId(), found.getTitle(), found.getStatus())).orElse(null);
        return new NetworkIdeaDtos.Idea(idea.getId(), idea.getTitle(), idea.getDescription(), idea.getStatus(), idea.getStatusNote(), count, voted,
            userId.equals(idea.getAuthorId()), author, idea.getCreatedAt(), idea.getStatusChangedAt(), into);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
