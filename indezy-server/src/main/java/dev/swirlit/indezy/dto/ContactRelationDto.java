package dev.swirlit.indezy.dto;

/**
 * A contact {@code contactId} works with: {@code otherId}, named, at its company (a client or an ESN), and how they
 * work together.
 */
public record ContactRelationDto(
    Long contactId,
    Long otherId,
    String otherName,
    Long otherClientId,
    String otherClientName,
    Boolean otherClientIsFinal,
    String note) {

    // Spring MVC logs bodies through toString at DEBUG: the ids only, never a name (see docs/security.md).
    @Override
    public String toString() {
        return "ContactRelationDto[contactId=" + contactId + ", otherId=" + otherId + "]";
    }
}
