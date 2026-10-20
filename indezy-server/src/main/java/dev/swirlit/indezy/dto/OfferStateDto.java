package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.swirlit.indezy.model.OfferApplication;

import java.util.Collection;
import java.util.Objects;

/** What the caller's workspace did with an offer. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferStateDto(Long offerId, boolean saved, boolean hidden, boolean seen, Long importedProjectId, String note,
                            OfferApplication application) {

    public OfferStateDto(Long offerId, boolean saved, boolean hidden, boolean seen, Long importedProjectId) {
        this(offerId, saved, hidden, seen, importedProjectId, null, null);
    }

    /**
     * What the workspace did with a mission, from its states on each copy: saving, hiding or seeing any copy counts, and
     * the first note and application found are the mission's.
     */
    public static OfferStateDto merged(Long offerId, Collection<OfferStateDto> states) {
        return new OfferStateDto(offerId,
            states.stream().anyMatch(OfferStateDto::saved),
            states.stream().anyMatch(OfferStateDto::hidden),
            states.stream().anyMatch(OfferStateDto::seen),
            states.stream().map(OfferStateDto::importedProjectId).filter(Objects::nonNull).findFirst().orElse(null),
            states.stream().map(OfferStateDto::note).filter(Objects::nonNull).findFirst().orElse(null),
            states.stream().map(OfferStateDto::application).filter(Objects::nonNull).findFirst().orElse(null));
    }
}
