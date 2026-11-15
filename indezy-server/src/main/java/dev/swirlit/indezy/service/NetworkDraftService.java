package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkDraft;
import dev.swirlit.indezy.repository.NetworkDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * A member's drafts (see docs/features.md#member-network): kept to publish by hand, or scheduled, then published on their
 * own within a minute of their time. Publishing makes an ordinary post, the draft going.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkDraftService {

    static final int MAX_DRAFTS = 50;
    static final Duration EARLIEST = Duration.ofMinutes(1);
    static final Duration LATEST = Duration.ofDays(30);
    private static final int BATCH = 20;

    private final NetworkService network;
    private final NetworkPostService posts;
    private final NetworkDraftRepository drafts;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Value("${indezy.network.drafts-publishing:true}")
    private boolean publishing = true;

    /** The member's drafts, the scheduled ones first by their time, then the others, the latest written first. */
    @Transactional(readOnly = true)
    public List<NetworkDtos.Draft> list(Long userId) {
        network.requireMember(userId);
        return drafts.findByAuthorIdOrderByCreatedAtDescIdDesc(userId).stream()
            .sorted(Comparator.comparing((NetworkDraft draft) -> draft.getScheduledAt() == null)
                .thenComparing(draft -> draft.getScheduledAt() == null ? Instant.MIN : draft.getScheduledAt()))
            .map(NetworkDraftService::toDto).toList();
    }

    @Transactional
    public NetworkDtos.Draft create(Long userId, NetworkDtos.DraftRequest request) {
        network.requireMember(userId);
        if (drafts.countByAuthorId(userId) >= MAX_DRAFTS) {
            throw new InvalidStateException("A member keeps " + MAX_DRAFTS + " drafts at most");
        }
        NetworkDraft draft = new NetworkDraft();
        draft.setAuthorId(userId);
        write(draft, request);
        return toDto(drafts.save(draft));
    }

    @Transactional
    public NetworkDtos.Draft update(Long userId, Long id, NetworkDtos.DraftRequest request) {
        network.requireMember(userId);
        NetworkDraft draft = own(userId, id);
        write(draft, request);
        return toDto(draft);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        network.requireMember(userId);
        drafts.delete(own(userId, id));
    }

    /** Publishes a draft now, as an ordinary post. */
    @Transactional
    public NetworkDtos.Post publish(Long userId, Long id) {
        NetworkDraft draft = own(userId, id);
        NetworkDtos.Post post = posts.post(userId, new NetworkDtos.NewPost(draft.getText()));
        drafts.delete(draft);
        return post;
    }

    /** Publishes, every minute, the scheduled drafts whose time came; a draft that cannot be published yet waits. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void publishDue() {
        if (!publishing) {
            return;
        }
        for (NetworkDraft due : drafts.findByScheduledAtLessThanEqualOrderByScheduledAtAsc(clock.instant(), PageRequest.of(0, BATCH))) {
            try {
                transaction.executeWithoutResult(status -> publish(due.getAuthorId(), due.getId()));
            } catch (RuntimeException e) {
                // Away from the network, over the hourly limit, or published by another replica first: it waits.
                log.debug("Scheduled draft {} not published: {}", due.getId(), e.getClass().getSimpleName());
            }
        }
    }

    private void write(NetworkDraft draft, NetworkDtos.DraftRequest request) {
        Instant at = request.scheduledAt();
        Instant now = clock.instant();
        if (at != null && (at.isBefore(now.plus(EARLIEST)) || at.isAfter(now.plus(LATEST)))) {
            throw new IllegalArgumentException("A post is scheduled from a minute to 30 days ahead");
        }
        draft.setText(request.text().strip());
        draft.setScheduledAt(at);
    }

    private NetworkDraft own(Long userId, Long id) {
        return drafts.findByIdAndAuthorId(id, userId).orElseThrow(() -> new ResourceNotFoundException("No such draft"));
    }

    private static NetworkDtos.Draft toDto(NetworkDraft draft) {
        return new NetworkDtos.Draft(draft.getId(), draft.getText(), draft.getScheduledAt(), draft.getUpdatedAt());
    }
}
