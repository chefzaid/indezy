package dev.swirlit.indezy.tax;

import dev.swirlit.indezy.model.enums.MicroActivity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/**
 * The French figures that change by law, per year (see docs/features.md#revenue-ceilings): the micro-entreprise
 * revenue ceiling and the VAT franchise thresholds for services, and the micro-entrepreneur's social contribution
 * rates. Updated each January from service-public.gouv.fr (checked 2026-11-23: the pages verified on 2026-01-01 and
 * 2026-02-21); a year without its own figures uses the latest known ones, which {@link Year#year()} names.
 */
public final class FrenchFigures {

    /**
     * One year's figures, amounts in euros excluding VAT and rates in percent of the revenue.
     *
     * @param microServicesCeiling the micro-entreprise ceiling for services (BIC services and BNC)
     * @param vatServicesBase      the VAT franchise threshold for services, against the previous year's revenue
     * @param vatServicesTolerance the higher threshold for the current year, lost on the day it is passed
     * @param bncRate              the contributions of a non-regulated liberal activity (BNC, SSI)
     * @param cipavRate            the contributions of a regulated liberal activity (BNC, CIPAV)
     * @param bicServicesRate      the contributions of commercial services (BIC); the rates are null for a year not checked
 * @param bncDischargeRate     the income tax paid with the contributions (versement libératoire) of a BNC activity
 * @param bicServicesDischargeRate the same for commercial services (BIC)
     */
    public record Year(int year, BigDecimal microServicesCeiling, BigDecimal vatServicesBase, BigDecimal vatServicesTolerance,
                       BigDecimal bncRate, BigDecimal cipavRate, BigDecimal bicServicesRate, BigDecimal bncDischargeRate,
                       BigDecimal bicServicesDischargeRate) {

        /** The rate in percent of the revenue for the activity, the income tax included when paid with it; null when not known for the year. */
        public BigDecimal rate(MicroActivity activity, boolean discharge) {
            BigDecimal contributions = switch (activity) {
                case BNC_SSI -> bncRate;
                case BNC_CIPAV -> cipavRate;
                case BIC_SERVICES -> bicServicesRate;
            };
            if (contributions == null || !discharge) {
                return contributions;
            }
            return contributions.add(activity == MicroActivity.BIC_SERVICES ? bicServicesDischargeRate : bncDischargeRate);
        }
    }

    private static final Map<Integer, Year> YEARS = new TreeMap<>(Map.of(
        // 2025's contribution rates were not checked against an official page: none is given.
        2025, new Year(2025, new BigDecimal("77700"), new BigDecimal("37500"), new BigDecimal("41250"), null, null, null, null, null),
        2026, new Year(2026, new BigDecimal("83600"), new BigDecimal("37500"), new BigDecimal("41250"),
            new BigDecimal("25.6"), new BigDecimal("23.2"), new BigDecimal("21.2"), new BigDecimal("2.2"), new BigDecimal("1.7"))));

    private FrenchFigures() {
    }

    /** The year's figures, or the closest known year's: the latest for a later year, the first for an earlier one. */
    public static Year of(int year) {
        TreeMap<Integer, Year> years = (TreeMap<Integer, Year>) YEARS;
        Map.Entry<Integer, Year> known = years.floorEntry(year);
        return known != null ? known.getValue() : years.firstEntry().getValue();
    }
}
