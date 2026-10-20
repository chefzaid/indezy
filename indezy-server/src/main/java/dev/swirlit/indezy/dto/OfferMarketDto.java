package dev.swirlit.indezy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The market the current filters describe (see {@code OfferMarketService}).
 *
 * @param offers         missions found, each counted once
 * @param rates          their daily rates
 * @param rateBins       how many offers ask each band of daily rates, in order
 * @param remote         how many are remote, hybrid or on site
 * @param skills         the most frequent skills
 * @param intermediaries the most frequent publishers: intermediaries (ESN, agencies) or clients
 * @param perDay         offers published each of the last days, oldest first
 */
public record OfferMarketDto(long offers, Rates rates, List<RateBin> rateBins, Remote remote, List<OfferFacetDto> skills,
                             List<OfferFacetDto> intermediaries, List<DayCount> perDay) {

    /**
     * Quartiles of the offers' daily rates (the middle of a stated range), absent without any rate.
     *
     * @param stated     offers stating a rate
     * @param target     the profile's target daily rate, if any
     * @param meetTarget percent of the offers stating a rate whose best rate reaches the target
     */
    public record Rates(long stated, Integer firstQuartile, Integer median, Integer thirdQuartile, Integer target,
                        Integer meetTarget) {
    }

    /** Daily rates from {@code from} included to {@code to} excluded; the first and last bands may be open. */
    public record RateBin(Integer from, Integer to, long offers) {
    }

    /**
     * @param hybridRemoteDays remote days a week of the hybrid offers stating them, on average
     */
    public record Remote(long remote, long hybrid, long onsite, long unstated, Double hybridRemoteDays) {
    }

    public record DayCount(LocalDate day, long offers) {
    }

    public OfferMarketDto {
        rateBins = List.copyOf(rateBins);
        skills = List.copyOf(skills);
        intermediaries = List.copyOf(intermediaries);
        perDay = List.copyOf(perDay);
    }
}
