package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferChange;
import dev.swirlit.indezy.model.enums.OfferChangeField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What its site changed on a stored offer it published again: a daily rate stated or changed, a start stated or moved,
 * a description rewritten. A detail the site stops stating is not a change.
 */
final class OfferChanges {

    private OfferChanges() {
    }

    /** The changes from {@code stored} to {@code collected}, the same offer read again. */
    static List<OfferChange> between(JobOffer stored, JobOffer collected) {
        List<OfferChange> changes = new ArrayList<>();
        String rate = rate(collected);
        if (rate != null && !rate.equals(rate(stored))) {
            changes.add(change(stored, collected, OfferChangeField.RATE, rate(stored), rate));
        }
        String start = start(collected);
        if (start != null && !start.equals(start(stored))) {
            changes.add(change(stored, collected, OfferChangeField.START, start(stored), start));
        }
        if (stored.getDescription() != null && collected.getDescription() != null &&
            !Objects.equals(stored.getDescription(), collected.getDescription())) {
            changes.add(change(stored, collected, OfferChangeField.DESCRIPTION, null, null));
        }
        return changes;
    }

    /** "min|max", either side empty, or {@code null} without a rate. */
    static String rate(JobOffer offer) {
        if (offer.getRateMin() == null && offer.getRateMax() == null) {
            return null;
        }
        return Objects.toString(offer.getRateMin(), "") + "|" + Objects.toString(offer.getRateMax(), "");
    }

    /** "ASAP", an ISO day, or {@code null} without a start. */
    static String start(JobOffer offer) {
        if (Boolean.TRUE.equals(offer.getStartAsap())) {
            return "ASAP";
        }
        return offer.getStartDate() == null ? null : offer.getStartDate().toString();
    }

    private static OfferChange change(JobOffer stored, JobOffer collected, OfferChangeField field, String before, String after) {
        OfferChange change = new OfferChange();
        change.setOffer(stored);
        change.setField(field);
        change.setBefore(before);
        change.setAfter(after);
        change.setChangedAt(collected.getLastSeenAt());
        return change;
    }
}
