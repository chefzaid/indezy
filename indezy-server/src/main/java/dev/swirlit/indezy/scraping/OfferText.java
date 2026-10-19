package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.util.TextFolding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the rates, remote policies, seniorities and durations that job boards write as text. */
public final class OfferText {

    /** Daily rates outside this range are annual salaries, hourly rates or typos. */
    public static final int MIN_DAILY_RATE = 100;
    public static final int MAX_DAILY_RATE = 3000;

    public record Rate(Integer min, Integer max) {
        public static final Rate NONE = new Rate(null, null);
    }

    /** A number of a rate text, and whether a currency or "TJM" sits next to it. */
    private record Amount(int value, int start, int end, boolean priced) {
        Amount withPrice() {
            return new Amount(value, start, end, true);
        }
    }

    public record Remote(WorkMode mode, Integer daysPerWeek) {
        public static final Remote UNKNOWN = new Remote(null, null);
    }

    /** "01/11/2026", "2026-11-01", "12:30": dates and times are never rates. */
    private static final Pattern DATES = Pattern.compile("\\d{1,4}[/.-]\\d{1,2}[/.-]\\d{1,4}|\\d{1,2}[:h]\\d{2}\\b");
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d \\u00A0\\u202F.]*\\d|\\d)(\\s*k)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY_AFTER = Pattern.compile("^\\s*(€|eur|euros?\\b|e\\b|/\\s*j|/\\s*jour|par jour|ht\\b|h\\.t)");
    private static final Pattern CURRENCY_BEFORE = Pattern.compile("(€|tjm|taux journalier|daily rate)\\s*:?\\s*$");
    /** What joins the two ends of a range: "450-550", "500 à 650", "500 et 650", "500 to 650". */
    private static final Pattern RANGE = Pattern.compile("\\s*(-|–|a|et|to|/)\\s*");
    private static final Pattern MAX_ONLY = Pattern.compile("\\b(max|maximum|jusqu\\W*a|up to|plafond)\\b\\s*:?\\s*\\d");
    private static final Pattern MIN_ONLY = Pattern.compile("\\b(min|minimum|a partir de|from|des)\\b\\s*:?\\s*\\d");
    private static final Pattern REMOTE_DAYS = Pattern.compile("(\\d)\\s*(?:j|jours?|days?)\\b[^.]{0,30}?(?:teletravail|remote|distance)" +
        "|(?:teletravail|remote)[^.]{0,20}?(\\d)\\s*(?:j|jours?|days?)\\b");
    private static final String DURATION_UNIT = "(?<unit>mois|months?|ans?|annees?|years?|semaines?|weeks?)";
    private static final Pattern TEXT_DURATION = Pattern.compile(
        "(?:duree(?: initiale| previsionnelle| estimee)?(?: de)?|duration|mission de|mission of|contrat de|pour une duree de)\\s*" +
            "(?<value>\\d{1,2})\\s*" + DURATION_UNIT + "\\b" +
            "|(?<value2>\\d{1,2})\\s*(?:mois|months?)\\s*(?:renouvelables?|renewable|minimum|fermes?|extensible)");
    private static final Pattern TITLE_DURATION = Pattern.compile("\\b(?<value>\\d{1,2})\\s*(?<unit>mois|months?)\\b");
    /** "6 mois", "6+ mois", "1 an", "465 jours ouvrés", "6 à 12 mois" (its lower bound). */
    private static final Pattern AMOUNT_OF_TIME = Pattern.compile(
        "\\b(\\d{1,3})\\+?\\s*(?:a\\s*\\d{1,3}\\s*)?(mois|months?|ans?|annees?|years?|semaines?|weeks?|jours?|days?)\\b");
    private static final Pattern SALARIED = Pattern.compile("\\b(cdi|cdd)\\b");
    private static final Pattern FREELANCE = Pattern.compile("\\b(freelances?|independants?|portage)\\b");
    private static final Pattern ONSITE_DAYS = Pattern.compile("(\\d)\\s*(?:j|jours?|days?)\\b[^.]{0,20}?(?:sur site|on site|onsite|presentiel)");

    private OfferText() {
    }

    /**
     * A daily rate in euros from text such as "TJM HT max 600 €", "450-550€/j", "entre 500 et 650" or
     * "470"; "Selon profil", annual salaries ("55k€") and out-of-range numbers give {@link Rate#NONE}.
     */
    public static Rate dailyRate(String text) {
        if (text == null) {
            return Rate.NONE;
        }
        String folded = DATES.matcher(TextFolding.fold(text)).replaceAll(" ");
        List<Amount> amounts = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(folded);
        while (matcher.find()) {
            int value = parseAmount(matcher.group(1));
            if (matcher.group(2) == null && value >= MIN_DAILY_RATE && value <= MAX_DAILY_RATE) {
                String before = folded.substring(Math.max(0, matcher.start() - 20), matcher.start());
                boolean priced = CURRENCY_AFTER.matcher(folded.substring(matcher.end())).find() ||
                    CURRENCY_BEFORE.matcher(before).find();
                amounts.add(new Amount(value, matcher.start(), matcher.end(), priced));
            }
        }
        // "450-550 €": the low end of a range takes the currency written after its high end.
        for (int i = amounts.size() - 2; i >= 0; i--) {
            Amount next = amounts.get(i + 1);
            if (next.priced() && RANGE.matcher(folded.substring(amounts.get(i).end(), next.start())).matches()) {
                amounts.set(i, amounts.get(i).withPrice());
            }
        }
        List<Integer> rates = amounts.stream().filter(Amount::priced).map(Amount::value).toList();
        if (rates.isEmpty()) {
            rates = amounts.stream().map(Amount::value).toList();
        }
        if (rates.isEmpty()) {
            return Rate.NONE;
        }
        int low = rates.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int high = rates.stream().mapToInt(Integer::intValue).max().orElseThrow();
        if (rates.size() == 1) {
            if (MAX_ONLY.matcher(folded).find()) {
                return new Rate(null, high);
            }
            if (MIN_ONLY.matcher(folded).find()) {
                return new Rate(low, null);
            }
        }
        return new Rate(low, high);
    }

    /**
     * The remote policy of text such as "Télétravail complet", "Hybride", "2 jours de télétravail par
     * semaine", "Sur site" or "partial".
     */
    public static Remote remote(String text) {
        String folded = TextFolding.words(text);
        if (folded == null || folded.isEmpty()) {
            return Remote.UNKNOWN;
        }
        Matcher remoteDays = REMOTE_DAYS.matcher(folded);
        if (remoteDays.find()) {
            int days = Integer.parseInt(remoteDays.group(1) != null ? remoteDays.group(1) : remoteDays.group(2));
            return remoteDaysPerWeek(days);
        }
        Matcher onsiteDays = ONSITE_DAYS.matcher(folded);
        if (onsiteDays.find()) {
            return remoteDaysPerWeek(Math.max(0, 5 - Integer.parseInt(onsiteDays.group(1))));
        }
        if (folded.matches(".*\\b(full remote|full time remote|100 teletravail|teletravail complet|teletravail total" +
            "|totalement a distance|fully remote|remote only|100 remote|a distance|distanciel|full distanciel)\\b.*") || "remote".equals(folded) ||
            "teletravail".equals(folded) || "full".equals(folded)) {
            return new Remote(WorkMode.REMOTE, 5);
        }
        if (folded.matches(".*\\b(hybride|hybrid|partiel|partial|teletravail partiel|mixte)\\b.*")) {
            return new Remote(WorkMode.HYBRID, null);
        }
        if (folded.matches(".*\\b(sur site|on site|onsite|presentiel|pas de teletravail|sans teletravail|no remote|none|aucun)\\b.*")) {
            return new Remote(WorkMode.ONSITE, 0);
        }
        return Remote.UNKNOWN;
    }

    /** On site at 0 days, full remote at 5 or more, hybrid between. */
    public static Remote remoteDaysPerWeek(int days) {
        if (days <= 0) {
            return new Remote(WorkMode.ONSITE, 0);
        }
        if (days >= 5) {
            return new Remote(WorkMode.REMOTE, 5);
        }
        return new Remote(WorkMode.HYBRID, days);
    }

    /**
     * A mission length written in text: "Durée : 6 mois", "12 mois renouvelables", "mission de 3 mois", or any "7 mois"
     * in a title. Years of experience ("5 ans d'expérience") are never read as a duration.
     */
    public static Integer durationInText(String text, boolean title) {
        String folded = TextFolding.words(text);
        if (folded == null || folded.isEmpty()) {
            return null;
        }
        Matcher matcher = (title ? TITLE_DURATION : TEXT_DURATION).matcher(folded);
        if (!matcher.find()) {
            return null;
        }
        if (matcher.group("value") != null) {
            return durationMonths(Double.valueOf(matcher.group("value")), matcher.group("unit"));
        }
        // "12 mois renouvelables" names no unit group: it is in months.
        return durationMonths(Double.valueOf(matcher.group("value2")), "mois");
    }

    /**
     * Whether a title names a permanent or fixed-term job and no freelance mission ("Tech Lead – CDI (H/F)"), which some
     * sites list among their freelance offers; "CDI/Freelance" names both and is kept.
     */
    public static boolean namesSalariedJob(String title) {
        String folded = TextFolding.words(title);
        return folded != null && SALARIED.matcher(folded).find() && !FREELANCE.matcher(folded).find();
    }

    /** A duration field as sites fill it ("6 mois", "1 an", "6+ mois", "465 jours ouvrés"), in months. */
    public static Integer duration(String text) {
        String folded = TextFolding.words(text);
        Matcher matcher = AMOUNT_OF_TIME.matcher(folded == null ? "" : folded);
        return matcher.find() ? durationMonths(Double.valueOf(matcher.group(1)), matcher.group(2)) : null;
    }

    /** "Junior", "Confirmé", "Intermédiaire", "Senior", "Expert" and their English forms. */
    public static Seniority seniority(String text) {
        String folded = TextFolding.words(text);
        if (folded == null || folded.isEmpty()) {
            return null;
        }
        if (folded.matches(".*\\b(expert|expertise|principal)\\b.*")) {
            return Seniority.EXPERT;
        }
        if (folded.matches(".*\\b(senior|sr)\\b.*")) {
            return Seniority.SENIOR;
        }
        if (folded.matches(".*\\b(confirme|confirmee|intermediaire|intermediate|mid|medior|experimente)\\b.*")) {
            return Seniority.CONFIRMED;
        }
        if (folded.matches(".*\\b(junior|debutant|jr|entry)\\b.*")) {
            return Seniority.JUNIOR;
        }
        return null;
    }

    /** Years of experience asked for: under 3 junior, under 6 confirmed, under 11 senior, then expert. */
    public static Seniority seniorityOfYears(Integer years) {
        if (years == null || years < 0) {
            return null;
        }
        if (years < 3) {
            return Seniority.JUNIOR;
        }
        if (years < 6) {
            return Seniority.CONFIRMED;
        }
        return years < 11 ? Seniority.SENIOR : Seniority.EXPERT;
    }

    /**
     * A duration in whole months from a value and its unit ("day", "jours", "week", "month", "mois",
     * "year", "an"), at least one month; {@code null} when either is missing or the unit is unknown.
     */
    public static Integer durationMonths(Double value, String unit) {
        String folded = TextFolding.words(unit);
        if (value == null || value <= 0 || folded == null) {
            return null;
        }
        double months;
        if (folded.startsWith("day") || folded.startsWith("jour") || "d".equals(folded) || "j".equals(folded)) {
            months = value / 21.0;
        } else if (folded.startsWith("week") || folded.startsWith("semaine")) {
            months = value / 4.33;
        } else if (folded.startsWith("month") || folded.startsWith("mois")) {
            months = value;
        } else if (folded.startsWith("year") || folded.startsWith("an")) {
            months = value * 12;
        } else {
            return null;
        }
        return (int) Math.max(1, Math.round(months));
    }

    private static int parseAmount(String digits) {
        String compact = digits.replaceAll("[\\s  ]", "");
        // "1.200" is a thousands separator, "600.00" a decimal part.
        if (compact.matches("\\d{1,2}\\.\\d{3}")) {
            compact = compact.replace(".", "");
        } else if (compact.contains(".")) {
            compact = compact.substring(0, compact.indexOf('.'));
        }
        try {
            return compact.isEmpty() ? 0 : Integer.parseInt(compact);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
