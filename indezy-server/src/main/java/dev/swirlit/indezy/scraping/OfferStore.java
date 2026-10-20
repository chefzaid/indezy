package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferSkip;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferSkipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saves the offers of one page in one transaction: new ones are added, known ones refreshed, and those the feed left
 * out are remembered as skipped.
 */
@Service
@RequiredArgsConstructor
public class OfferStore {

    /** The column's length; longer identifiers are not remembered. */
    private static final int MAX_EXTERNAL_ID_LENGTH = 191;
    /** Offers given their copy keys at once. */
    static final int KEY_BATCH = 500;

    private final JobOfferRepository offers;
    private final OfferSkipRepository skips;

    /** How many of {@code collected} (normalized offers of {@code site}) Indezy had never seen. */
    @Transactional
    public int save(OfferSite site, List<JobOffer> collected) {
        if (collected.isEmpty()) {
            return 0;
        }
        Map<String, JobOffer> byExternalId = new LinkedHashMap<>();
        collected.forEach(offer -> byExternalId.putIfAbsent(offer.getExternalId(), offer));
        Map<String, JobOffer> known = offers.findBySiteAndExternalIdIn(site, byExternalId.keySet()).stream()
            .collect(Collectors.toMap(JobOffer::getExternalId, Function.identity()));
        List<JobOffer> created = new ArrayList<>();
        for (JobOffer offer : byExternalId.values()) {
            JobOffer existing = known.get(offer.getExternalId());
            if (existing == null) {
                created.add(offer);
            } else {
                existing.updateFrom(offer);
            }
        }
        offers.saveAll(created);
        byExternalId.values().forEach(offer -> linkCopies(site, known.getOrDefault(offer.getExternalId(), offer)));
        return created.size();
    }

    /**
     * Gives up to {@value #KEY_BATCH} offers stored without copy keys their keys, from what is stored of them, and
     * groups them with their copies; how many were given keys.
     */
    @Transactional
    public int keyStoredOffers() {
        List<JobOffer> unkeyed = offers.findByPublisherKeyIsNullOrderById(PageRequest.of(0, KEY_BATCH));
        unkeyed.forEach(offer -> {
            offer.setPublisherKey(OfferFingerprint.publisherKey(offer.getSite(), offer.getTitle(), offer.getCompany()));
            offer.setOpeningKey(OfferFingerprint.openingKey(offer.getTitle(), offer.getDescription()));
        });
        unkeyed.forEach(offer -> linkCopies(offer.getSite(), offer));
        return (int) unkeyed.stream().filter(offer -> offer.getPublisherKey() != null).count();
    }

    /**
     * Gives up to {@value #KEY_BATCH} offers stored before publishers had a key, after {@code afterId}, their publisher's
     * key; returns the last offer looked at, or {@code null} when none was left.
     */
    @Transactional
    public Long keyStoredCompanies(Long afterId) {
        List<JobOffer> unkeyed = offers.findByCompanyIsNotNullAndCompanyKeyIsNullAndIdGreaterThanOrderById(afterId, PageRequest.of(0, KEY_BATCH));
        unkeyed.forEach(offer -> offer.setCompany(offer.getCompany()));
        return unkeyed.isEmpty() ? null : unkeyed.getLast().getId();
    }

    /**
     * Puts an offer in one group with its copies on other sites, merging the groups they were in; the group is named
     * after its smallest offer id, so it does not change as copies join.
     */
    private void linkCopies(OfferSite site, JobOffer offer) {
        if (offer.getPublisherKey() == null) {
            return;
        }
        List<JobOffer> members = new ArrayList<>(offers.findCopyCandidates(site, offer.getPublisherKey(), offer.getOpeningKey())
            .stream().filter(candidate -> isCopy(offer, candidate)).toList());
        if (members.isEmpty()) {
            return;
        }
        members.add(offer);
        Set<Long> groups = members.stream().map(JobOffer::getCopyGroup).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!groups.isEmpty()) {
            members.addAll(offers.findByCopyGroupIn(groups));
        }
        long group = members.stream()
            .mapToLong(member -> member.getCopyGroup() == null ? member.getId() : Math.min(member.getCopyGroup(), member.getId()))
            .min().orElseThrow();
        members.forEach(member -> member.setCopyGroup(group));
    }

    /**
     * Two offers of different sites publish one mission when their text opens the same, or when their publisher is
     * the same and they are not placed in two different departments (see {@link OfferFingerprint}).
     */
    static boolean isCopy(JobOffer offer, JobOffer other) {
        boolean sameOpening = offer.getOpeningKey() != null && offer.getOpeningKey().equals(other.getOpeningKey());
        boolean samePublisher = offer.getPublisherKey().equals(other.getPublisherKey()) &&
            (offer.getDepartmentCode() == null || other.getDepartmentCode() == null ||
                offer.getDepartmentCode().equals(other.getDepartmentCode()));
        return sameOpening || samePublisher;
    }

    /** Remembers offers the feed left out, so readers of one page per offer do not fetch them again. */
    @Transactional
    public void skip(OfferSite site, Collection<String> externalIds, Instant now) {
        Set<String> fresh = new HashSet<>();
        for (String id : externalIds) {
            if (id != null && id.length() <= MAX_EXTERNAL_ID_LENGTH) {
                fresh.add(id);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        fresh.removeAll(skips.findSkippedIds(site, fresh));
        skips.saveAll(fresh.stream().map(id -> new OfferSkip(site, id, now)).toList());
    }

    /** Which identifiers of a site are stored or recently skipped. */
    @Transactional(readOnly = true)
    public Set<String> known(OfferSite site, Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return Set.of();
        }
        Set<String> known = new HashSet<>(offers.findKnownIds(site, externalIds));
        known.addAll(skips.findSkippedIds(site, externalIds));
        return known;
    }
}
