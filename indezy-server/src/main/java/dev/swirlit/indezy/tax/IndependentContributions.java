package dev.swirlit.indezy.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The social contributions of an individual entrepreneur under the real regime (EI au réel, SSI), on a year's profit,
 * at the rates applying from 2026 (entreprendre.service-public.gouv.fr F33351, "Protection sociale du commerçant et de
 * l'artisan", checked on 2026-11-24, décret n° 2024-688): the base is the profit less a flat 26 % abatement; then
 * health and maternity on a rate rising with the base (0 % under 20 % of the PASS to 8.5 % at three PASS), daily
 * allowances 0.5 % from 40 % of the PASS, the base pension 17.87 % up to the PASS and 0.72 % on the whole base, the
 * supplementary pension 8.1 % up to the PASS and 9.1 % up to four, disability and death 1.3 % up to the PASS, family
 * allowances on a rate rising from 0 % at 110 % of the PASS to 3.1 % at 140 %, the CSG and CRDS 9.7 %, and the training
 * contribution 0.25 % of the PASS. Not estimated past three PASS (the page stops there), nor the first two years'
 * flat amounts, nor the minimum contributions of a very low income.
 */
public final class IndependentContributions {

    /** The annual social security ceiling (PASS) of 2026. */
    public static final BigDecimal PASS_2026 = new BigDecimal("48060");
    /** The year whose rates these are. */
    public static final int YEAR = 2026;
    static final BigDecimal ABATEMENT = new BigDecimal("0.26");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** A line of the contributions. */
    public enum Kind {
        HEALTH,
        DAILY_ALLOWANCES,
        BASE_PENSION,
        SUPPLEMENTARY_PENSION,
        DISABILITY,
        FAMILY,
        CSG_CRDS,
        TRAINING
    }

    /** A contribution and its amount, in euros. */
    public record Line(Kind kind, BigDecimal amount) {
    }

    /** The base after the abatement, the lines and their total. */
    public record Contributions(BigDecimal base, List<Line> lines, BigDecimal total) {

        public Contributions {
            lines = List.copyOf(lines);
        }
    }

    private IndependentContributions() {
    }

    /** The contributions on {@code profit} (revenue less expenses, before contributions); empty past three PASS. */
    public static Optional<Contributions> of(BigDecimal profit) {
        BigDecimal pass = PASS_2026;
        BigDecimal base = profit.signum() <= 0 ? BigDecimal.ZERO : profit.multiply(BigDecimal.ONE.subtract(ABATEMENT));
        if (base.compareTo(pass.multiply(BigDecimal.valueOf(3))) > 0) {
            return Optional.empty();
        }
        BigDecimal capped = base.min(pass);
        List<Line> lines = new ArrayList<>();
        lines.add(line(Kind.HEALTH, percent(base, healthRate(base, pass))));
        lines.add(line(Kind.DAILY_ALLOWANCES, base.compareTo(share(pass, "0.4")) >= 0 ? percent(base, new BigDecimal("0.5")) : BigDecimal.ZERO));
        lines.add(line(Kind.BASE_PENSION, percent(capped, new BigDecimal("17.87")).add(percent(base, new BigDecimal("0.72")))));
        lines.add(line(Kind.SUPPLEMENTARY_PENSION, percent(capped, new BigDecimal("8.1"))
            .add(percent(base.min(pass.multiply(BigDecimal.valueOf(4))).subtract(capped), new BigDecimal("9.1")))));
        lines.add(line(Kind.DISABILITY, percent(capped, new BigDecimal("1.3"))));
        lines.add(line(Kind.FAMILY, percent(base, familyRate(base, pass))));
        lines.add(line(Kind.CSG_CRDS, percent(base, new BigDecimal("9.7"))));
        lines.add(line(Kind.TRAINING, percent(pass, new BigDecimal("0.25"))));
        BigDecimal total = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return Optional.of(new Contributions(base.setScale(2, RoundingMode.HALF_UP), lines, total));
    }

    /**
     * The health and maternity rate, in percent, on the whole base: 0 under 20 % of the PASS, then rising in straight
     * lines to 1.5 % at 40 %, 4 % at 60 %, 6.5 % at 110 %, 7.7 % at two PASS and 8.5 % at three. (For the last band, the
     * page's formula reads 1.2 per PASS but its table 7.7 % to 8.5 %: the table is followed.)
     */
    static BigDecimal healthRate(BigDecimal base, BigDecimal pass) {
        String[][] points = {{"0.2", "0"}, {"0.4", "1.5"}, {"0.6", "4"}, {"1.1", "6.5"}, {"2", "7.7"}, {"3", "8.5"}};
        return between(base, pass, points);
    }

    /** The family allowances rate, in percent: 0 under 110 % of the PASS, 3.1 % from 140 %, in a straight line between. */
    static BigDecimal familyRate(BigDecimal base, BigDecimal pass) {
        return between(base, pass, new String[][] {{"1.1", "0"}, {"1.4", "3.1"}});
    }

    /** The rate at {@code base} on the straight lines through the points (share of the PASS, rate), flat outside them. */
    private static BigDecimal between(BigDecimal base, BigDecimal pass, String[][] points) {
        if (base.compareTo(share(pass, points[0][0])) < 0) {
            return new BigDecimal(points[0][1]);
        }
        for (int i = 1; i < points.length; i++) {
            BigDecimal from = share(pass, points[i - 1][0]);
            BigDecimal to = share(pass, points[i][0]);
            if (base.compareTo(to) <= 0) {
                BigDecimal low = new BigDecimal(points[i - 1][1]);
                BigDecimal high = new BigDecimal(points[i][1]);
                return low.add(high.subtract(low).multiply(base.subtract(from)).divide(to.subtract(from), 6, RoundingMode.HALF_UP));
            }
        }
        return new BigDecimal(points[points.length - 1][1]);
    }

    private static BigDecimal share(BigDecimal pass, String share) {
        return pass.multiply(new BigDecimal(share));
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static Line line(Kind kind, BigDecimal amount) {
        return new Line(kind, amount.setScale(2, RoundingMode.HALF_UP));
    }
}
