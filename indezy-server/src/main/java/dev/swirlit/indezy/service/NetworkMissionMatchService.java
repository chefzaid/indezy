package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferMatchDto;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkPostDetails;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.scraping.GeoLocator;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The missions members pass on, scored against the reader's profile as the feed's offers are (see
 * docs/features.md#freelance-offer-feed): their role and stack for the skills, their daily rate against the target, fully
 * remote or their city against the profile's, their start against the availability, with the profile's weights.
 */
@Service
@RequiredArgsConstructor
public class NetworkMissionMatchService {

    private static final int FULL_REMOTE = 5;

    private final OfferSearchService offers;
    private final GeoLocator geoLocator;

    /** Each mission's match, by post id; empty when the profile names no skills. */
    @Transactional(readOnly = true)
    public Map<Long, OfferMatchDto> scores(Long freelanceId, Collection<NetworkPost> posts) {
        OfferMatch.Profile profile = offers.profile(freelanceId);
        if (!profile.canMatch()) {
            return Map.of();
        }
        Map<String, Optional<GeoLocator.Place>> places = new HashMap<>();
        Map<Long, OfferMatchDto> scores = new LinkedHashMap<>();
        for (NetworkPost post : posts) {
            if (post.getDetails() != null) {
                JobOffer offer = offer(post.getDetails(), places);
                OfferMatchDto match = OfferMatch.score(offer, offer.getSkills(), profile);
                if (match != null) {
                    scores.put(post.getId(), match);
                }
            }
        }
        return scores;
    }

    /** A mission read as an offer, its city located once per call. */
    private JobOffer offer(NetworkPostDetails details, Map<String, Optional<GeoLocator.Place>> places) {
        JobOffer offer = new JobOffer();
        List<String> stack = details.getStack() == null ? List.of() :
            Arrays.stream(details.getStack().split(",")).map(String::strip).filter(skill -> !skill.isEmpty()).toList();
        offer.setSkills(stack);
        offer.setSearchText(TextFolding.words((details.getRole() == null ? "" : details.getRole()) + " " + String.join(" ", stack)));
        offer.setRateMin(details.getRateMin());
        offer.setRateMax(details.getRateMax());
        Integer remote = details.getRemoteDays();
        offer.setWorkMode(remote == null ? null : remote >= FULL_REMOTE ? WorkMode.REMOTE : remote == 0 ? WorkMode.ONSITE : WorkMode.HYBRID);
        offer.setRemoteDaysPerWeek(remote);
        offer.setStartDate(details.getStart());
        if (details.getCity() != null && !details.getCity().isBlank()) {
            places.computeIfAbsent(TextFolding.words(details.getCity()), key -> geoLocator.locate(details.getCity(), null)).ifPresent(place -> {
                offer.setLatitude(place.latitude());
                offer.setLongitude(place.longitude());
            });
        }
        return offer;
    }
}
