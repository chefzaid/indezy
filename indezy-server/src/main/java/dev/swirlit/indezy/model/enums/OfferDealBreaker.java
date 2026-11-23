package dev.swirlit.indezy.model.enums;

import dev.swirlit.indezy.dto.OfferMatchDto;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.util.TextFolding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What can rule an offer out before it is opened (see docs/features.md#deal-breakers), read from the offer itself and
 * its match with the profile, without AI: on site every day, a rate under the profile's target by 10 percent or more,
 * farther than the profile's commute reaches, a start before the profile is available, portage required, or a
 * permanent job posted as a mission.
 */
public enum OfferDealBreaker {
    ON_SITE_ONLY,
    RATE_UNDER_TARGET,
    TOO_FAR,
    STARTS_TOO_EARLY,
    PORTAGE_REQUIRED,
    PERMANENT_JOB;

    /** Portage asked for as the only way in, in French or English. */
    private static final Pattern PORTAGE = Pattern.compile(
        "portage (salarial )?(obligatoire|uniquement|exclusivement|exige|impose|requis|only|required|mandatory)" +
            "|(uniquement|exclusivement|seulement|only) (en |via |par |through |in )?portage" +
            "|(pas|aucun) de (freelance|independant|facturation directe)");

    /** A permanent contract offered, not a mission: "poste en CDI", "CDI uniquement", "permanent position". */
    private static final Pattern PERMANENT = Pattern.compile(
        "(poste|contrat|recrutement|embauche|recrute|propose) (en |un )?cdi\\b|\\bcdi (uniquement|exclusivement|only)" +
            "|permanent (position|contract|role) only|\\bpas de (mission|freelance) cdi\\b");

    /** The offer's deal-breakers in this order; none when nothing rules it out. */
    public static List<OfferDealBreaker> of(JobOffer offer, OfferMatchDto match) {
        List<OfferDealBreaker> found = new ArrayList<>();
        if (offer.getWorkMode() == WorkMode.ONSITE) {
            found.add(ON_SITE_ONLY);
        }
        if (match != null && match.rate() == OfferMatchDto.RateFit.BELOW) {
            found.add(RATE_UNDER_TARGET);
        }
        if (match != null && match.place() == OfferMatchDto.PlaceFit.FAR) {
            found.add(TOO_FAR);
        }
        if (match != null && match.availability() == OfferMatchDto.AvailabilityFit.TOO_EARLY) {
            found.add(STARTS_TOO_EARLY);
        }
        String text = TextFolding.words((offer.getTitle() == null ? "" : offer.getTitle()) + " " +
            (offer.getDescription() == null ? "" : offer.getDescription()));
        if (PORTAGE.matcher(text).find()) {
            found.add(PORTAGE_REQUIRED);
        }
        if (PERMANENT.matcher(text).find()) {
            found.add(PERMANENT_JOB);
        }
        return found;
    }
}
