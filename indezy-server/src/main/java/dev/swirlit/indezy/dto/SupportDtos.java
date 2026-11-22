package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.SupportRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;

/** The support requests' messages (see docs/features.md#support-requests). */
public final class SupportDtos {

    private SupportDtos() {
    }

    /** A request a member sends; {@code context} is null when they chose not to send it. */
    public record NewRequest(@NotNull SupportRequest.Kind kind, @NotBlank @Size(max = SupportRequest.SUBJECT_MAX) String subject,
                             @NotBlank @Size(max = SupportRequest.DESCRIPTION_MAX) String description,
                             @Size(max = SupportRequest.CONTEXT_MAX) String context) {

        // Spring MVC logs bodies through toString at DEBUG: never the member's text.
        @Override
        public String toString() {
            return "NewRequest[kind=" + kind + "]";
        }
    }

    /** A request as its author sees it, with the answer once given. */
    public record Request(Long id, SupportRequest.Kind kind, String subject, String description, String context, SupportRequest.Status status,
                          String answer, LocalDateTime createdAt, Instant answeredAt) {

        @Override
        public String toString() {
            return "Request[id=" + id + "]";
        }
    }

    /** A request as the administrators see it: its author's address, to write to them otherwise. */
    public record Received(Request request, Long userId, String email) {

        @Override
        public String toString() {
            return "Received[id=" + (request == null ? null : request.id()) + "]";
        }
    }

    /** An administrator's answer, and whether it closes the request. */
    public record Answer(@NotBlank @Size(max = SupportRequest.ANSWER_MAX) String answer, boolean close) {

        @Override
        public String toString() {
            return "Answer[close=" + close + "]";
        }
    }
}
