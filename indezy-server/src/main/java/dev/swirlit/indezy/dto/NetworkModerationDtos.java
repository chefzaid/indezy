package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.NetworkReport;
import dev.swirlit.indezy.model.enums.NetworkReportOutcome;
import dev.swirlit.indezy.model.enums.NetworkReportReason;
import dev.swirlit.indezy.model.enums.NetworkReportTarget;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Reports made by members, and the moderation queue of the admin console. */
public final class NetworkModerationDtos {

    private NetworkModerationDtos() {
    }

    /** A member's report: why, and in a few words what is wrong. */
    public record NewReport(@NotNull NetworkReportReason reason, @Size(max = NetworkReport.DETAILS_MAX) String details) {

        // Spring MVC logs bodies through toString at DEBUG: not what a member wrote.
        @Override
        public String toString() {
            return "NewReport[reason=" + reason + ", length=" + (details == null ? 0 : details.length()) + "]";
        }
    }

    /** An account as the moderators see it: its handle and name, if it still has them. */
    public record Party(Long accountId, String handle, String name) {

        @Override
        public String toString() {
            return "Party[accountId=" + accountId + "]";
        }
    }

    /** A report, open or decided, with how many open reports name the same target. */
    public record Report(Long id, NetworkReportTarget targetType, Long targetId, Party target, String excerpt, NetworkReportReason reason,
                         String details, Party reporter, LocalDateTime createdAt, long openOnTarget, NetworkReportOutcome outcome,
                         Party resolvedBy, Instant resolvedAt, String note) {

        @Override
        public String toString() {
            return "Report[id=" + id + ", targetType=" + targetType + ", targetId=" + targetId + ", outcome=" + outcome + "]";
        }
    }

    /** The open reports, the oldest first, and the latest decisions. */
    public record Queue(List<Report> open, List<Report> resolved, long openCount, List<Held> held, List<NetworkReviewDtos.Held> heldReviews) {

        public Queue {
            open = List.copyOf(open);
            resolved = List.copyOf(resolved);
            held = List.copyOf(held);
            heldReviews = List.copyOf(heldReviews);
        }
    }

    /** A post, or a comment under a post, the spam score held, with why. */
    public record Held(Long id, Party author, Long postId, String text, List<String> reasons, LocalDateTime createdAt) {

        public Held {
            reasons = List.copyOf(reasons);
        }

        @Override
        public String toString() {
            return "Held[id=" + id + ", postId=" + postId + ", reasons=" + reasons + "]";
        }
    }

    /** The moderator's decision, applied to every open report on the same target; the days for a suspension. */
    public record Decision(@NotNull NetworkReportOutcome outcome, @Size(max = NetworkReport.NOTE_MAX) String note,
                           @Min(1) @Max(90) Integer days) {

        @Override
        public String toString() {
            return "Decision[outcome=" + outcome + ", days=" + days + "]";
        }
    }
}
