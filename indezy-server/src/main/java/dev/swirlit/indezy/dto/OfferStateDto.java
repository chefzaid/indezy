package dev.swirlit.indezy.dto;

import java.util.Collection;
import java.util.Objects;

/** What the caller's workspace did with an offer. */
public record OfferStateDto(Long offerId, boolean saved, boolean hidden, boolean seen, Long importedProjectId) {

    /** What the workspace did with a mission, from its states on each copy: saving, hiding or seeing any copy counts. */
    public static OfferStateDto merged(Long offerId, Collection<OfferStateDto> states) {
        return new OfferStateDto(offerId,
            states.stream().anyMatch(OfferStateDto::saved),
            states.stream().anyMatch(OfferStateDto::hidden),
            states.stream().anyMatch(OfferStateDto::seen),
            states.stream().map(OfferStateDto::importedProjectId).filter(Objects::nonNull).findFirst().orElse(null));
    }
}
