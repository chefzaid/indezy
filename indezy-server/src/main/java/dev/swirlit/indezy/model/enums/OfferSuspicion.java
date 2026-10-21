package dev.swirlit.indezy.model.enums;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Why an offer looks like a fake or spam listing (see docs/features.md#suspicious-offers). An offer keeps its reasons as
 * bits, one per constant in declaration order: add new reasons at the end, never reorder them.
 */
public enum OfferSuspicion {
    /** It names no publisher and has no description. */
    NO_PUBLISHER_NO_DESCRIPTION,
    /** Its daily rate is below 40% or above 250% of the median of its skill. */
    RATE_OUTSIDE_MARKET,
    /** Its opening text is posted by four publishers or more. */
    SAME_TEXT_MANY_PUBLISHERS,
    /** Its publisher is known for fake listings. */
    KNOWN_FAKE_PUBLISHER;

    public int bit() {
        return 1 << ordinal();
    }

    /** The bits of {@code reasons}, or {@code null} without any. */
    public static Integer mask(Collection<OfferSuspicion> reasons) {
        int mask = reasons.stream().mapToInt(OfferSuspicion::bit).reduce(0, (a, b) -> a | b);
        return mask == 0 ? null : mask;
    }

    /** The reasons of {@code mask}, or {@code null} without any. */
    public static List<OfferSuspicion> of(Integer mask) {
        if (mask == null || mask == 0) {
            return null;
        }
        return Arrays.stream(values()).filter(reason -> (mask & reason.bit()) != 0).toList();
    }
}
