package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.NetworkReview;
import dev.swirlit.indezy.model.enums.NetworkReviewKind;
import dev.swirlit.indezy.model.enums.NetworkReviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Members' reviews of the clients and ESNs they worked with. */
public final class NetworkReviewDtos {

    private NetworkReviewDtos() {
    }

    /** A review as its author writes it: three ratings out of five and a text that names no contact. */
    public record Draft(
        @NotNull @Min(1) @Max(5) Integer paymentRating,
        @NotNull @Min(1) @Max(5) Integer processRating,
        @NotNull @Min(1) @Max(5) Integer rateRating,
        @NotBlank @Size(min = 20, max = NetworkReview.TEXT_MAX) String text) {

        // Spring MVC logs bodies through toString at DEBUG: not what a member wrote.
        @Override
        public String toString() {
            return "Draft[ratings=" + paymentRating + "/" + processRating + "/" + rateRating + "]";
        }
    }

    /** A published review, without its author; {@code mine} when the reader wrote it. */
    public record Review(Long id, NetworkReviewKind kind, int paymentRating, int processRating, int rateRating, String text,
                         LocalDateTime updatedAt, boolean mine) {
    }

    /** The reader's own review of the company, whatever its status, with why it is held. */
    public record Mine(Long id, NetworkReviewStatus status, int paymentRating, int processRating, int rateRating, String text,
                       List<String> heldReasons, LocalDateTime updatedAt) {

        public Mine {
            heldReasons = List.copyOf(heldReasons);
        }
    }

    /**
     * What the network says of one of the workspace's clients: its published reviews and their averages (null without
     * any), the reader's own, and whether they may write one (a member with a won mission with it).
     */
    public record Company(String companyName, NetworkReviewKind kind, int count, Double paymentAverage, Double processAverage,
                          Double rateAverage, List<Review> reviews, Mine mine, boolean canReview) {

        public Company {
            reviews = List.copyOf(reviews);
        }
    }

    /** A review held for an administrator, with its author and why it was held. */
    public record Held(Long id, NetworkModerationDtos.Party author, String companyName, NetworkReviewKind kind, int paymentRating,
                       int processRating, int rateRating, String text, List<String> reasons, LocalDateTime createdAt) {

        public Held {
            reasons = List.copyOf(reasons);
        }

        @Override
        public String toString() {
            return "Held[id=" + id + ", reasons=" + reasons + "]";
        }
    }

    /** An administrator's decision on a held review: published (with its text edited, when given) or rejected. */
    public record Release(boolean publish, @Size(max = NetworkReview.TEXT_MAX) String text) {

        @Override
        public String toString() {
            return "Release[publish=" + publish + "]";
        }
    }
}
