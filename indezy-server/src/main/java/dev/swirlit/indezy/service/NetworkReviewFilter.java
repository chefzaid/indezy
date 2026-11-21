package dev.swirlit.indezy.service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What holds a review of a client or an ESN for an administrator before it is shown (see docs/security.md#member-network):
 * anything that looks like spam, insults, accusations a court could call defamation, or personal data. Any one reason
 * holds it: a review names a company, and a wrong one shown costs more than one waiting a day.
 */
final class NetworkReviewFilter {

    static final int SHOUTING_LETTERS = 30;
    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+|\\b[\\w-]+\\.(?:com|fr|io|net|org)\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    /** A French number (06 12 34 56 78, +33 6 12 34 56 78) or any run of nine digits or more. */
    private static final Pattern PHONE = Pattern.compile("(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(?0\\)?[\\s.-]?)?[1-9](?:[\\s.-]?\\d{2}){4}|\\d{9,}");
    /** Insults and slurs, folded (no accents, lower case), matched as whole words. */
    private static final List<String> INSULTS = List.of("connard", "connards", "connasse", "connasses", "conard", "conards", "encule",
        "encules", "enculee", "salope", "salopes", "salaud", "salauds", "pute", "putes", "batards", "bitches", "assholes", "bastards",
        "fdp", "ntm", "batard", "pd", "negre", "bougnoule", "youpin", "bitch", "asshole", "bastard", "fuck", "fucking", "cunt", "retard",
        "moron", "idiot", "idiots", "abruti", "abrutis", "debile", "debiles");
    /** Accusations of an offence: a fact for a court to establish, not a review to state. */
    private static final List<String> ACCUSATIONS = List.of("escroc", "escrocs", "escroquerie", "arnaque", "arnaqueur", "arnaqueurs",
        "voleur", "voleurs", "voleuse", "fraude", "fraudeur", "illegal", "illegale", "scam", "scammer", "scammers", "fraud", "thief",
        "thieves", "crook", "crooks");

    /** Why a review is held. */
    enum Reason {
        LINKS, EMAIL, PHONE, CONTACT_NAME, INSULT, ACCUSATION, REPEATED, SHOUTING
    }

    private NetworkReviewFilter() {
    }

    /**
     * The reasons the text is held, given the names of the author's contacts at the company (never to be named) and
     * the author's other reviews.
     */
    static Set<Reason> reasons(String text, Collection<String> contactNames, Collection<String> otherReviews) {
        Set<Reason> reasons = EnumSet.noneOf(Reason.class);
        if (EMAIL.matcher(text).find()) {
            reasons.add(Reason.EMAIL);
        }
        if (LINK.matcher(EMAIL.matcher(text).replaceAll(" ")).find()) {
            reasons.add(Reason.LINKS);
        }
        if (PHONE.matcher(text).find()) {
            reasons.add(Reason.PHONE);
        }
        String folded = " " + fold(text) + " ";
        if (contactNames.stream().map(NetworkReviewFilter::fold).filter(name -> name.length() >= 3)
                .anyMatch(name -> folded.contains(" " + name + " "))) {
            reasons.add(Reason.CONTACT_NAME);
        }
        if (INSULTS.stream().anyMatch(word -> folded.contains(" " + word + " "))) {
            reasons.add(Reason.INSULT);
        }
        if (ACCUSATIONS.stream().anyMatch(word -> folded.contains(" " + word + " "))) {
            reasons.add(Reason.ACCUSATION);
        }
        String normalized = fold(text);
        if (otherReviews.stream().map(NetworkReviewFilter::fold).anyMatch(normalized::equals)) {
            reasons.add(Reason.REPEATED);
        }
        long letters = text.codePoints().filter(Character::isLetter).count();
        long capitals = text.codePoints().filter(Character::isUpperCase).count();
        if (letters >= SHOUTING_LETTERS && capitals * 10 >= letters * 7) {
            reasons.add(Reason.SHOUTING);
        }
        return reasons;
    }

    /** Lower case without accents, every run of other characters than letters and digits made one space. */
    static String fold(String value) {
        String plain = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
