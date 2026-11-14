package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.TestimonialStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Testimonials as their freelancer and their author see them, and the requests that write them. */
public final class TestimonialDtos {

    private TestimonialDtos() {
    }

    /** A testimonial as its freelancer sees it. */
    public record Testimonial(Long id, Long contactId, String recipientName, TestimonialStatus status, Instant expiresAt, String authorName,
                              String authorTitle, String text, Instant receivedAt) {
    }

    /** Ask a testimonial of a contact of the workspace, or of someone named. */
    public record Request(Long contactId, @Size(max = 120) String recipientName) {
    }

    /** The asked testimonial and its link's token, shown once; its {@code toString} leaves the token out of the logs. */
    public record Link(String token, Testimonial testimonial) {
        @Override
        public String toString() {
            return "Link[testimonial=" + testimonial + "]";
        }
    }

    /** Show a received testimonial on the public profile, or hide it. */
    public record StatusChange(@NotNull TestimonialStatus status) {
    }

    /** What the author sees behind the link: who asks, of whom, and whether it was already written. */
    public record PublicRequest(String freelancerName, String recipientName, boolean answered) {
    }

    /** The author's testimonial; they agree to be named with it on the freelancer's public profile. */
    public record Answer(@NotBlank @Size(max = 120) String authorName, @Size(max = 160) String authorTitle,
                         @NotBlank @Size(min = 20, max = 1500) String text, @AssertTrue boolean consent) {
    }

    /** An approved testimonial on the public profile. */
    public record Shown(String authorName, String authorTitle, String text) {
    }
}
