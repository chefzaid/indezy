package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferPublisherDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferPublisherBlock;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferPublisherBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** What a publisher puts in the feed, for its page (see {@link OfferPublisherDto}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferPublisherInsightService {

    /** The span its republished missions are counted over. */
    static final Duration REPUBLISH_WINDOW = Duration.ofDays(90);
    /** Open offers listed on its page at most. */
    static final int MAX_OFFERS = 20;

    private final JobOfferRepository offers;
    private final ClientRepository clients;
    private final OfferPublisherBlockRepository blocks;
    private final Clock clock;

    /** The publisher named {@code name} however it is written; 404 when Indezy holds none of its offers. */
    public OfferPublisherDto insights(Long freelanceId, String name) {
        String key = JobOffer.companyKeyOf(name);
        List<JobOffer> all = key == null ? List.of() : offers.findByCompanyKeyOrderByPublishedAtDesc(key);
        if (all.isEmpty()) {
            throw new ResourceNotFoundException(ErrorMessages.OFFER_PUBLISHER_NOT_FOUND);
        }
        Instant now = clock.instant();
        List<JobOffer> open = all.stream().filter(offer -> offer.getExpiresAt() == null || offer.getExpiresAt().isAfter(now)).toList();
        Set<Object> missions = new HashSet<>();
        open.forEach(offer -> missions.add(offer.getCopyGroup() != null ? offer.getCopyGroup() : "offer-" + offer.getId()));
        List<Integer> rates = open.stream().map(offer -> OfferRateBenchmarkService.middle(offer.getRateMin(), offer.getRateMax()))
            .filter(Objects::nonNull).toList();
        List<Integer> ages = open.stream().map(offer -> (int) Duration.between(offer.getPublishedAt(), now).toDays()).toList();
        Map<String, Set<String>> referencesByMission = new HashMap<>();
        all.stream()
            .filter(offer -> offer.getPublisherKey() != null && !offer.getPublishedAt().isBefore(now.minus(REPUBLISH_WINDOW)))
            .forEach(offer -> referencesByMission.computeIfAbsent(offer.getSite() + "|" + offer.getPublisherKey(), mission -> new HashSet<>())
                .add(offer.getExternalId()));
        return new OfferPublisherDto(key, name(all), missions.size(), rates.isEmpty() ? null : OfferRateBenchmarkService.median(rates),
            ages.isEmpty() ? null : OfferRateBenchmarkService.median(ages), referencesByMission.size(),
            (int) referencesByMission.values().stream().filter(references -> references.size() > 1).count(), sites(open),
            client(freelanceId, key), blocks.findByFreelanceIdAndPublisherKey(freelanceId, key).map(OfferPublisherBlock::getId).orElse(null),
            open.stream().limit(MAX_OFFERS).map(OfferPublisherInsightService::summary).toList());
    }

    /** The spelling its offers use most, the newest among equals. */
    private static String name(List<JobOffer> offers) {
        Map<String, Integer> counts = new HashMap<>();
        offers.forEach(offer -> counts.merge(offer.getCompany(), 1, Integer::sum));
        int most = counts.values().stream().max(Integer::compare).orElse(0);
        return offers.stream().map(JobOffer::getCompany).filter(company -> counts.get(company) == most).findFirst().orElseThrow();
    }

    private static List<OfferPublisherDto.SiteOffers> sites(List<JobOffer> open) {
        Map<OfferSite, Integer> bySite = new EnumMap<>(OfferSite.class);
        open.forEach(offer -> bySite.merge(offer.getSite(), 1, Integer::sum));
        return bySite.entrySet().stream()
            .sorted(Map.Entry.<OfferSite, Integer>comparingByValue().reversed().thenComparing(entry -> entry.getKey().getDisplayName()))
            .map(entry -> new OfferPublisherDto.SiteOffers(entry.getKey(), entry.getKey().getDisplayName(), entry.getValue()))
            .toList();
    }

    private OfferPublisherDto.Client client(Long freelanceId, String key) {
        return clients.findByFreelanceId(freelanceId).stream()
            .filter(client -> key.equals(JobOffer.companyKeyOf(client.getCompanyName())))
            .min(Comparator.comparing(client -> client.getId()))
            .map(client -> new OfferPublisherDto.Client(client.getId(), client.getCompanyName(), Boolean.FALSE.equals(client.getIsFinal())))
            .orElse(null);
    }

    private static OfferPublisherDto.Summary summary(JobOffer offer) {
        return new OfferPublisherDto.Summary(offer.getId(), offer.getTitle(), offer.getSite().getDisplayName(), offer.getRateMin(),
            offer.getRateMax(), offer.getCity(), offer.getPublishedAt());
    }
}
