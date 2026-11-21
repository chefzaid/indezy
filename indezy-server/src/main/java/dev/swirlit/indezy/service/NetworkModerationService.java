package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkModerationDtos;
import dev.swirlit.indezy.dto.NetworkReviewDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkComment;
import dev.swirlit.indezy.model.NetworkHeld;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkReport;
import dev.swirlit.indezy.model.NetworkReview;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.model.enums.NetworkReportOutcome;
import dev.swirlit.indezy.model.enums.NetworkReportReason;
import dev.swirlit.indezy.model.enums.NetworkReportTarget;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkHeldRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkReportRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports and the moderation queue (see docs/features.md#member-network): a member reports a post, a comment, a message
 * or a member they can see, once while it is open; an administrator decides, the decision falling on every open report of the same
 * target, the author told of a sanction and each reporter of the outcome. Decided reports stay as the audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkModerationService {

    static final int SHOWN = 100;
    static final int DEFAULT_SUSPENSION_DAYS = 7;
    static final int DASHBOARD_COMPANIES = 10;

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final NetworkPostService postService;
    private final NetworkCommentService commentService;
    private final NetworkMessageService messageService;
    private final NetworkNotificationService notices;
    private final NetworkLimits limits;
    private final NetworkReportRepository reports;
    private final NetworkHeldRepository held;
    private final NetworkPostRepository posts;
    private final NetworkCommentRepository comments;
    private final NetworkMemberRepository members;
    private final NetworkRestrictionRepository restrictions;
    private final UserRepository users;
    private final AccountEventService events;
    private final Clock clock;
    private final NetworkFileService files;
    private final NetworkReviewService reviewService;

    @Transactional
    public void reportPost(Long userId, Long postId, NetworkModerationDtos.NewReport request) {
        NetworkPost post = postService.readable(userId, postId);
        save(userId, NetworkReportTarget.POST, post.getId(), post.getAuthorId(), post.getText(), request);
    }

    @Transactional
    public void reportComment(Long userId, Long commentId, NetworkModerationDtos.NewReport request) {
        NetworkComment comment = comments.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException(NetworkCommentService.NOT_FOUND));
        NetworkPost post = postService.readable(userId, comment.getPostId());
        if (!NetworkPostService.visible(post, comment, userId, Set.copyOf(restrictions.blockedEitherWay(userId)))) {
            throw new ResourceNotFoundException(NetworkCommentService.NOT_FOUND);
        }
        save(userId, NetworkReportTarget.COMMENT, comment.getId(), comment.getAuthorId(), comment.getText(), request);
    }

    /** Reports a message of the reporter's conversation: its text goes to the moderators with the report. */
    @Transactional
    public void reportMessage(Long userId, Long messageId, NetworkModerationDtos.NewReport request) {
        network.requireMember(userId);
        NetworkMessage message = messageService.readable(userId, messageId);
        if (message.isDeleted()) {
            throw new InvalidStateException("A deleted message cannot be reported");
        }
        save(userId, NetworkReportTarget.MESSAGE, message.getId(), message.getSenderId(), message.getText(), request);
    }

    /** Reports a published review of a client or an ESN: its text goes to the moderators with the report. */
    @Transactional
    public void reportReview(Long userId, Long reviewId, NetworkModerationDtos.NewReport request) {
        NetworkReview review = reviewService.readable(userId, reviewId);
        save(userId, NetworkReportTarget.REVIEW, review.getId(), review.getAuthorId(), review.getText(), request);
    }

    @Transactional
    public void reportMember(Long userId, String handle, NetworkModerationDtos.NewReport request) {
        NetworkMember member = follows.visible(network.requireMember(userId), handle);
        save(userId, NetworkReportTarget.MEMBER, member.getUserId(), member.getUserId(), null, request);
    }

    private void save(Long userId, NetworkReportTarget type, Long targetId, Long targetUserId, String excerpt,
                      NetworkModerationDtos.NewReport request) {
        if (userId.equals(targetUserId)) {
            throw new InvalidStateException("You cannot report yourself");
        }
        limits.report(userId);
        if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndOutcomeIsNull(userId, type, targetId)) {
            throw new InvalidStateException("You already reported this: the moderators will look at it");
        }
        NetworkReport report = new NetworkReport();
        report.setReporterId(userId);
        report.setTargetType(type);
        report.setTargetId(targetId);
        report.setTargetUserId(targetUserId);
        report.setExcerpt(excerpt);
        report.setReason(request.reason());
        String details = request.details() == null ? "" : request.details().strip();
        report.setDetails(details.isEmpty() ? null : details);
        reports.save(report);
        log.info("Member {} reported {} {}", userId, type, targetId);
    }

    /** The open reports, the oldest first, and the latest decisions, for an administrator. */
    @Transactional(readOnly = true)
    public NetworkModerationDtos.Queue queue() {
        Map<Long, NetworkModerationDtos.Party> parties = new HashMap<>();
        List<NetworkReport> open = reports.findByOutcomeIsNullOrderByCreatedAtAscIdAsc(PageRequest.of(0, SHOWN));
        Map<String, Long> openOnTarget = new HashMap<>();
        open.forEach(report -> openOnTarget.merge(key(report), 1L, Long::sum));
        return new NetworkModerationDtos.Queue(
            open.stream().map(report -> toDto(report, openOnTarget.get(key(report)), parties)).toList(),
            reports.findByOutcomeIsNotNullOrderByResolvedAtDescIdDesc(PageRequest.of(0, SHOWN)).stream()
                .map(report -> toDto(report, 0, parties)).toList(),
            reports.countByOutcomeIsNull(),
            held.findAllByOrderByCreatedAtAscIdAsc(PageRequest.of(0, SHOWN)).stream().map(kept -> new NetworkModerationDtos.Held(kept.getId(),
                parties.computeIfAbsent(kept.getAuthorId(), this::party), kept.getPostId(), kept.getText(),
                List.of(kept.getReasons().split(",")), kept.getCreatedAt())).toList(),
            reviewService.held(author -> parties.computeIfAbsent(author, this::party)));
    }

    /** The reports of the last {@code days} days at a glance, for an administrator. */
    @Transactional(readOnly = true)
    public NetworkModerationDtos.Dashboard dashboard(int days) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<NetworkReport> recent = reports.findByCreatedAtAfter(now.minusDays(days));
        Map<NetworkReportReason, Long> byReason = new EnumMap<>(NetworkReportReason.class);
        Map<NetworkReportTarget, Long> byTarget = new EnumMap<>(NetworkReportTarget.class);
        long[] ages = new long[3];
        Map<Long, long[]> byReview = new HashMap<>();
        for (NetworkReport report : recent) {
            byReason.merge(report.getReason(), 1L, Long::sum);
            byTarget.merge(report.getTargetType(), 1L, Long::sum);
            boolean open = report.getOutcome() == null;
            if (open) {
                LocalDateTime made = report.getCreatedAt();
                ages[made.isAfter(now.minusDays(1)) ? 0 : made.isAfter(now.minusWeeks(1)) ? 1 : 2]++;
            }
            if (report.getTargetType() == NetworkReportTarget.REVIEW) {
                long[] counts = byReview.computeIfAbsent(report.getTargetId(), id -> new long[2]);
                counts[0] += open ? 1 : 0;
                counts[1]++;
            }
        }
        Map<String, long[]> byCompany = new HashMap<>();
        byReview.forEach((reviewId, counts) -> reviewService.companyName(reviewId).ifPresent(name -> {
            long[] sum = byCompany.computeIfAbsent(name, key -> new long[2]);
            sum[0] += counts[0];
            sum[1] += counts[1];
        }));
        List<NetworkModerationDtos.ReportedCompany> companies = byCompany.entrySet().stream()
            .map(entry -> new NetworkModerationDtos.ReportedCompany(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
            .sorted(Comparator.comparingLong(NetworkModerationDtos.ReportedCompany::total).reversed()
                .thenComparing(NetworkModerationDtos.ReportedCompany::companyName)).limit(DASHBOARD_COMPANIES).toList();
        return new NetworkModerationDtos.Dashboard(days, recent.size(), ages[0] + ages[1] + ages[2], byReason, byTarget, ages[0], ages[1],
            ages[2], companies);
    }

    /** Publishes a held review, edited or not, or rejects it; it leaves the queue either way. */
    @Transactional
    public NetworkModerationDtos.Queue releaseReview(Long adminId, Long reviewId, NetworkReviewDtos.Release release) {
        reviewService.release(adminId, reviewId, release);
        return queue();
    }

    /**
     * Lets a held post or comment go, published as its author's now, or rejects it, telling the author it was removed;
     * either way it leaves the queue. One that can no longer be published (its author or its post gone, the comments
     * closed) is refused: reject it.
     */
    @Transactional
    public NetworkModerationDtos.Queue release(Long adminId, Long heldId, boolean publish) {
        NetworkHeld kept = held.findById(heldId).orElseThrow(() -> new ResourceNotFoundException("Nothing held with this id"));
        if (publish) {
            try {
                if (kept.getPostId() == null) {
                    files.release(kept.getId(), postService.publish(kept.getAuthorId(), kept.getText()).id());
                } else {
                    commentService.publish(kept.getAuthorId(), kept.getPostId(), kept.getParentId(), kept.getText());
                }
            } catch (ResourceNotFoundException | InvalidStateException | IllegalArgumentException e) {
                throw new InvalidStateException("It can no longer be published: reject it");
            }
        } else {
            files.deleteForHeld(kept.getId());
            notices.moderation(kept.getAuthorId(), NetworkNotificationType.CONTENT_REMOVED);
            events.record(kept.getAuthorId(), AccountEventType.NETWORK_SANCTIONED, NetworkReportOutcome.REMOVED.name());
        }
        held.delete(kept);
        log.info("Administrator {} {} held {} of member {}", adminId, publish ? "published" : "rejected", heldId, kept.getAuthorId());
        return queue();
    }

    /**
     * Decides an open report, and every open report of the same target with it: removes the post or the comment, warns,
     * suspends or bans its author, as decided, telling them; each reporter is told whether the moderators acted.
     */
    @Transactional
    public NetworkModerationDtos.Queue decide(Long adminId, Long reportId, NetworkModerationDtos.Decision decision) {
        NetworkReport report = reports.findById(reportId).filter(found -> found.getOutcome() == null)
            .orElseThrow(() -> new ResourceNotFoundException("No open report with this id"));
        NetworkReportOutcome outcome = decision.outcome();
        if (outcome == NetworkReportOutcome.REMOVED && report.getTargetType() == NetworkReportTarget.MEMBER) {
            throw new InvalidStateException("A member cannot be removed: suspend or ban them");
        }
        apply(report, outcome, decision.days() == null ? DEFAULT_SUSPENSION_DAYS : decision.days());
        String note = decision.note() == null || decision.note().isBlank() ? null : decision.note().strip();
        List<NetworkReport> decided = reports.findByTargetTypeAndTargetIdAndOutcomeIsNull(report.getTargetType(), report.getTargetId());
        decided.forEach(each -> {
            each.setOutcome(outcome);
            each.setResolvedBy(adminId);
            each.setResolvedAt(clock.instant());
            each.setNote(note);
        });
        decided.stream().map(NetworkReport::getReporterId).distinct().forEach(reporter -> notices.moderation(reporter,
            outcome == NetworkReportOutcome.DISMISSED ? NetworkNotificationType.REPORT_DISMISSED : NetworkNotificationType.REPORT_ACTED));
        log.info("Administrator {} decided {} on {} {} ({} reports)", adminId, outcome, report.getTargetType(), report.getTargetId(),
            decided.size());
        return queue();
    }

    private void apply(NetworkReport report, NetworkReportOutcome outcome, int days) {
        if (outcome == NetworkReportOutcome.DISMISSED) {
            return;
        }
        Long author = report.getTargetUserId();
        switch (outcome) {
            case WARNED -> notices.moderation(author, NetworkNotificationType.WARNED);
            case REMOVED -> {
                if (report.getTargetType() == NetworkReportTarget.POST) {
                    posts.findById(report.getTargetId()).ifPresent(postService::remove);
                } else if (report.getTargetType() == NetworkReportTarget.REVIEW) {
                    reviewService.remove(report.getTargetId());
                } else if (report.getTargetType() == NetworkReportTarget.MESSAGE) {
                    // The removed message's card goes with its text.
                    messageService.removeByModerators(report.getTargetId());
                } else {
                    comments.findById(report.getTargetId()).ifPresent(commentService::remove);
                }
                notices.moderation(author, NetworkNotificationType.CONTENT_REMOVED);
            }
            case SUSPENDED -> {
                members.findByUserId(author).ifPresent(member -> member.setSuspendedUntil(clock.instant().plus(Duration.ofDays(days))));
                notices.moderation(author, NetworkNotificationType.SUSPENDED);
            }
            default -> members.findByUserId(author).ifPresent(member -> {
                member.setActive(false);
                member.setBannedAt(clock.instant());
            });
        }
        events.record(author, AccountEventType.NETWORK_SANCTIONED, outcome.name());
    }

    private static String key(NetworkReport report) {
        return report.getTargetType() + ":" + report.getTargetId();
    }

    private NetworkModerationDtos.Report toDto(NetworkReport report, long openOnTarget, Map<Long, NetworkModerationDtos.Party> parties) {
        return new NetworkModerationDtos.Report(report.getId(), report.getTargetType(), report.getTargetId(),
            parties.computeIfAbsent(report.getTargetUserId(), this::party), report.getExcerpt(), report.getReason(), report.getDetails(),
            parties.computeIfAbsent(report.getReporterId(), this::party), report.getCreatedAt(), openOnTarget, report.getOutcome(),
            report.getResolvedBy() == null ? null : parties.computeIfAbsent(report.getResolvedBy(), this::party), report.getResolvedAt(),
            report.getNote());
    }

    private NetworkModerationDtos.Party party(Long accountId) {
        Optional<User> user = users.findById(accountId).filter(found -> found.getDeletedAt() == null);
        return new NetworkModerationDtos.Party(accountId, members.findByUserId(accountId).map(NetworkMember::getHandle).orElse(null),
            user.map(found -> (Objects.toString(found.getFirstName(), "") + " " + Objects.toString(found.getLastName(), "")).strip())
                .orElse(null));
    }
}
