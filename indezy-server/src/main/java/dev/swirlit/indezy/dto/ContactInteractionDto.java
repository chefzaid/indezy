package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.model.enums.ReplyKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An exchange with a contact, maybe about one of the workspace's opportunities ({@code projectId}). {@code replied} only
 * applies to an {@code OUTGOING} one; {@code id}, {@code contactId}, {@code contactName}, {@code projectRole} and
 * {@code email} (set when Indezy sent it) are read-only.
 */
public record ContactInteractionDto(
    Long id,
    Long contactId,
    String contactName,
    @NotNull LocalDate occurredOn,
    @NotNull InteractionChannel channel,
    @NotNull InteractionDirection direction,
    Boolean replied,
    @Size(max = ContactInteraction.SUMMARY_MAX) String summary,
    Long projectId,
    String projectRole,
    SentEmail email,
    /** Read-only: what an answer that came back through Indezy says, as the AI sorted it; null otherwise. */
    ReplyKind replyKind,
    /** Read-only: for a "not now" answer, the day it said to write back, when the follow-up comes due. */
    LocalDate writeBackOn) {

    /** An exchange Indezy sent as an email: when, whether it tracks opening, and when and how often it was seen opened. */
    public record SentEmail(Instant sentAt, boolean tracked, Instant openedAt, int opens) {
    }

    // Spring MVC logs bodies through toString at DEBUG: never the summary (see docs/security.md).
    @Override
    public String toString() {
        return "ContactInteractionDto[id=" + id + ", channel=" + channel + ", direction=" + direction + "]";
    }
}
