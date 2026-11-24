package dev.swirlit.indezy.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The social contributions on a SASU president's gross pay of a year (assimilé salarié), at the payroll rates of
 * 1 January 2026 (open.lefebvre-dalloz.fr, "Taux des cotisations sur salaires au 1er janvier 2026", checked on
 * 2026-11-24; the employee rates match entreprendre.service-public.gouv.fr F2302). A president is covered as an employee
 * but not by unemployment insurance, so the unemployment, AGS and Apec contributions are left out; so are those that
 * depend on the company's staff (training, apprenticeship, the mobility levy from 11 employees, the death insurance of
 * executives) and the payroll tax, not due by a company charging VAT. Health and family allowances are at their full
 * rates (13 % and 5.25 %), the reduced ones not applied. The accident-at-work rate is the company's, given by the
 * member; without it, that line is left out.
 */
public final class PresidentContributions {

    private static final BigDecimal PASS = IndependentContributions.PASS_2026;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** The CSG and CRDS are on 98.25 % of the pay up to four PASS (the 1.75 % allowance for expenses), all of it beyond. */
    private static final BigDecimal CSG_SHARE = new BigDecimal("0.9825");

    /** A line of the payslip. */
    public enum Kind {
        HEALTH,
        AUTONOMY,
        FAMILY,
        PENSION_UNCAPPED,
        PENSION_CAPPED,
        HOUSING,
        SOCIAL_DIALOGUE,
        ACCIDENTS,
        CSG_CRDS,
        AGIRC_ARRCO,
        BALANCE,
        TECHNICAL_BALANCE
    }

    /** A contribution: the company's share and the president's, in euros. */
    public record Line(Kind kind, BigDecimal employer, BigDecimal employee) {
    }

    /** The lines, the company's total and the president's (taken from the gross pay). */
    public record Payslip(List<Line> lines, BigDecimal employer, BigDecimal employee) {

        public Payslip {
            lines = List.copyOf(lines);
        }
    }

    private PresidentContributions() {
    }

    /** The contributions on {@code gross}, the year's gross pay, with the company's {@code accidentRate} in percent (null: left out). */
    public static Payslip of(BigDecimal gross, BigDecimal accidentRate) {
        BigDecimal pay = gross.max(BigDecimal.ZERO);
        BigDecimal t1 = pay.min(PASS);
        BigDecimal t2 = pay.min(PASS.multiply(BigDecimal.valueOf(8))).subtract(t1).max(BigDecimal.ZERO);
        BigDecimal fourPass = PASS.multiply(BigDecimal.valueOf(4));
        BigDecimal csgBase = pay.min(fourPass).multiply(CSG_SHARE).add(pay.subtract(fourPass).max(BigDecimal.ZERO));
        List<Line> lines = new ArrayList<>();
        lines.add(line(Kind.HEALTH, percent(pay, "13"), BigDecimal.ZERO));
        lines.add(line(Kind.AUTONOMY, percent(pay, "0.30"), BigDecimal.ZERO));
        lines.add(line(Kind.FAMILY, percent(pay, "5.25"), BigDecimal.ZERO));
        lines.add(line(Kind.PENSION_UNCAPPED, percent(pay, "2.11"), percent(pay, "0.40")));
        lines.add(line(Kind.PENSION_CAPPED, percent(t1, "8.55"), percent(t1, "6.90")));
        lines.add(line(Kind.HOUSING, percent(t1, "0.10"), BigDecimal.ZERO));
        lines.add(line(Kind.SOCIAL_DIALOGUE, percent(pay, "0.016"), BigDecimal.ZERO));
        if (accidentRate != null) {
            lines.add(line(Kind.ACCIDENTS, pay.multiply(accidentRate).divide(HUNDRED, 2, RoundingMode.HALF_UP), BigDecimal.ZERO));
        }
        lines.add(line(Kind.CSG_CRDS, BigDecimal.ZERO, percent(csgBase, "9.70")));
        lines.add(line(Kind.AGIRC_ARRCO, percent(t1, "4.72").add(percent(t2, "12.95")), percent(t1, "3.15").add(percent(t2, "8.64"))));
        lines.add(line(Kind.BALANCE, percent(t1, "1.29").add(percent(t2, "1.62")), percent(t1, "0.86").add(percent(t2, "1.08"))));
        if (pay.compareTo(PASS) > 0) {
            BigDecimal cet = t1.add(t2);
            lines.add(line(Kind.TECHNICAL_BALANCE, percent(cet, "0.21"), percent(cet, "0.14")));
        }
        BigDecimal employer = lines.stream().map(Line::employer).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal employee = lines.stream().map(Line::employee).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new Payslip(lines, employer, employee);
    }

    private static BigDecimal percent(BigDecimal amount, String rate) {
        return amount.multiply(new BigDecimal(rate)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static Line line(Kind kind, BigDecimal employer, BigDecimal employee) {
        return new Line(kind, employer.setScale(2, RoundingMode.HALF_UP), employee.setScale(2, RoundingMode.HALF_UP));
    }
}
