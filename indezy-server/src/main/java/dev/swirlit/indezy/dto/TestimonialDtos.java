package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.TestimonialStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Testimonials as their freelancer and their author see them, and the requests that write them. */
public final class TestimonialDtos {

    private TestimonialDtos() {
    }

    /** A testimonial as its freelancer sees it. */
    public record Testimonial(Long id, Long contactId, String recipientName, String recipientEmail, TestimonialStatus status,
                              Instant expiresAt, String authorName, String authorTitle, String text, Instant receivedAt) {
    }

    /**
     * Ask a testimonial of a contact of the workspace, or of an external person: their first and last names and the email
     * the link goes to. A name alone ({@code recipientName}) still asks for a link to give by hand, as before.
     */
    public record Request(Long contactId, @Size(max = 120) String recipientName, @Size(max = 60) String recipientFirstName,
                          @Size(max = 60) String recipientLastName, @Email @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") @Size(max = 254) String recipientEmail) {

        public Request(Long contactId, String recipientName) {
            this(contactId, recipientName, null, null, null);
        }
    }

    /**
     * The asked testimonial and its link's token, shown once, and whether the link was emailed to its recipient; its
     * {@code toString} leaves the token out of the logs.
     */
    public record Link(String token, Testimonial testimonial, boolean emailed) {

        public Link(String token, Testimonial testimonial) {
            this(token, testimonial, false);
        }

        public Link emailed(boolean sent) {
            return new Link(token, testimonial, sent);
        }

        @Override
        public String toString() {
            return "Link[testimonial=" + testimonial + ", emailed=" + emailed + "]";
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
