package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the emails of the saved searches, in HTML and plain text, in the language the workspace chose: the new offers
 * with their rate, place, remote policy and match score, each linking to its page in Indezy, and the note sent when
 * sending stops. Every email carries a one-click unsubscribe (RFC 8058) besides its visible link, and no tracking
 * pixel: an offer opened from an email counts as a click once its page is open in the app.
 */
@Component
public class SavedSearchEmailComposer {

    private static final String ACCENT = "#4F46E5";
    private static final String MUTED = "#6B7280";

    private final MailProperties properties;

    public SavedSearchEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    /** One saved search's email: its offers found up to {@code cutoff}, {@code total} of them in all. */
    public record Digest(Long searchId, String searchName, String recipient, DocumentLanguage language, String token,
                         List<OfferDto> offers, long total, Instant cutoff) {

        public Digest {
            offers = List.copyOf(offers);
        }

        @Override
        public String toString() {
            return "Digest[" + searchId + ", " + offers.size() + " offers]";
        }
    }

    public Email digest(Digest digest) {
        DocumentLanguage language = digest.language();
        String name = digest.searchName();
        int count = (int) Math.min(Integer.MAX_VALUE, digest.total());
        String subject = count == 1 ? SavedSearchEmailLabel.SUBJECT_ONE.format(language, name) :
            SavedSearchEmailLabel.SUBJECT_MANY.format(language, count, name);
        String heading = count == 1 ? SavedSearchEmailLabel.HEADING_ONE.format(language, name) :
            SavedSearchEmailLabel.HEADING_MANY.format(language, count, name);
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder(heading).append("\n\n");
        html.append("<h1 style=\"font-size:20px;margin:0 0 16px\">").append(escape(heading)).append("</h1>");
        for (OfferDto offer : digest.offers()) {
            String link = offerLink(offer.id(), digest.searchId());
            String facts = String.join(" · ", facts(offer, language));
            html.append("<div style=\"margin:0 0 16px\"><a href=\"").append(escape(link)).append("\" style=\"color:").append(ACCENT)
                .append(";font-size:16px;font-weight:600;text-decoration:none\">").append(escape(offer.title())).append("</a>");
            if (!facts.isEmpty()) {
                html.append("<div style=\"color:").append(MUTED).append(";font-size:14px;margin-top:4px\">").append(escape(facts)).append("</div>");
            }
            html.append("</div>");
            text.append(offer.title()).append('\n');
            if (!facts.isEmpty()) {
                text.append(facts).append('\n');
            }
            text.append(link).append("\n\n");
        }
        long more = digest.total() - digest.offers().size();
        String all = properties.appUrl() + "/offers";
        String allLabel = more > 0 ? SavedSearchEmailLabel.MORE.format(language, more) : SavedSearchEmailLabel.SEE_ALL.in(language);
        html.append("<p style=\"margin:24px 0\"><a href=\"").append(escape(all)).append("\" style=\"color:").append(ACCENT).append("\">")
            .append(escape(allLabel)).append("</a></p>");
        text.append(allLabel).append(": ").append(all).append("\n\n");
        footer(html, text, name, language, digest.token());
        return new Email(digest.recipient(), subject, page(html), text.toString(), unsubscribeHeaders(digest.token()),
            "saved-search/" + digest.searchId() + "/" + digest.cutoff().getEpochSecond());
    }

    /** The note that sending stopped, as no offer was opened from the emails for 60 days. */
    public Email stopped(Long searchId, String searchName, String recipient, DocumentLanguage language, String token, Instant at) {
        String heading = SavedSearchEmailLabel.STOPPED_HEADING.format(language, searchName);
        String body = SavedSearchEmailLabel.STOPPED_BODY.in(language);
        String action = SavedSearchEmailLabel.STOPPED_ACTION.in(language);
        String searches = properties.appUrl() + "/offers";
        StringBuilder html = new StringBuilder("<h1 style=\"font-size:20px;margin:0 0 16px\">").append(escape(heading)).append("</h1>")
            .append("<p>").append(escape(body)).append("</p><p style=\"margin:24px 0\"><a href=\"").append(escape(searches))
            .append("\" style=\"color:").append(ACCENT).append("\">").append(escape(action)).append("</a></p>");
        String text = heading + "\n\n" + body + "\n\n" + action + ": " + searches + "\n";
        return new Email(recipient, SavedSearchEmailLabel.STOPPED_SUBJECT.format(language, searchName), page(html), text, Map.of(),
            "saved-search/" + searchId + "/stopped/" + at.getEpochSecond());
    }

    /** What the card says of an offer: publisher, place, remote policy, rate, duration and match. */
    static List<String> facts(OfferDto offer, DocumentLanguage language) {
        List<String> facts = new ArrayList<>();
        if (offer.company() != null) {
            facts.add(offer.company());
        }
        String place = offer.city() != null ? offer.city() : offer.departmentName();
        if (place != null) {
            facts.add(place);
        }
        String remote = remote(offer.workMode(), offer.remoteDaysPerWeek(), language);
        if (remote != null) {
            facts.add(remote);
        }
        String rate = rate(offer.rateMin(), offer.rateMax(), language);
        if (rate != null) {
            facts.add(rate);
        }
        if (offer.durationMonths() != null) {
            facts.add(SavedSearchEmailLabel.DURATION.format(language, offer.durationMonths()));
        }
        if (offer.match() != null) {
            facts.add(SavedSearchEmailLabel.MATCH.format(language, offer.match().score()));
        }
        if (offer.suspicion() != null && !offer.suspicion().isEmpty()) {
            facts.add(SavedSearchEmailLabel.SUSPICIOUS.in(language));
        }
        return facts;
    }

    private static String remote(WorkMode mode, Integer remoteDays, DocumentLanguage language) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case REMOTE -> SavedSearchEmailLabel.REMOTE.in(language);
            case HYBRID -> remoteDays == null || remoteDays <= 0 ? SavedSearchEmailLabel.HYBRID.in(language) :
                SavedSearchEmailLabel.HYBRID_DAYS.format(language, remoteDays);
            case ONSITE -> SavedSearchEmailLabel.ONSITE.in(language);
        };
    }

    private static String rate(Integer min, Integer max, DocumentLanguage language) {
        if (min != null && max != null && !min.equals(max)) {
            return SavedSearchEmailLabel.RATE_RANGE.format(language, min, max);
        }
        Integer one = Objects.requireNonNullElse(max, min);
        return one == null ? null : SavedSearchEmailLabel.RATE_ONE.format(language, one);
    }

    private void footer(StringBuilder html, StringBuilder text, String searchName, DocumentLanguage language, String token) {
        String why = SavedSearchEmailLabel.WHY.format(language, searchName);
        String change = properties.appUrl() + "/offers";
        String unsubscribe = unsubscribePage(token);
        html.append("<p style=\"color:").append(MUTED).append(";font-size:12px;border-top:1px solid #E5E7EB;padding-top:16px\">")
            .append(escape(why)).append("<br><a href=\"").append(escape(change)).append("\" style=\"color:").append(MUTED).append("\">")
            .append(escape(SavedSearchEmailLabel.CHANGE.in(language))).append("</a> · <a href=\"").append(escape(unsubscribe))
            .append("\" style=\"color:").append(MUTED).append("\">").append(escape(SavedSearchEmailLabel.UNSUBSCRIBE.in(language)))
            .append("</a></p>");
        text.append("--\n").append(why).append('\n').append(SavedSearchEmailLabel.CHANGE.in(language)).append(": ").append(change).append('\n')
            .append(SavedSearchEmailLabel.UNSUBSCRIBE.in(language)).append(": ").append(unsubscribe).append('\n');
    }

    /** The offer's page in the app, telling it the offer was opened from this search's email. */
    String offerLink(Long offerId, Long searchId) {
        return properties.appUrl() + "/offers/" + offerId + "?search=" + searchId + "&from=email";
    }

    /** The unsubscribe page, its token after {@code #} so it reaches neither access logs nor Referer headers. */
    String unsubscribePage(String token) {
        return properties.appUrl() + "/emails/unsubscribe#" + token;
    }

    /** The one-click unsubscribe a mailbox posts to (RFC 8058), whose address must hold the token. */
    private Map<String, String> unsubscribeHeaders(String token) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe", "<" + properties.appUrl() + "/api/public/saved-search-emails/unsubscribe/" + token + ">");
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        return headers;
    }

    private static String page(StringBuilder body) {
        return "<!doctype html><html><body style=\"margin:0;padding:24px;background:#F9FAFB\">" +
            "<div style=\"max-width:600px;margin:0 auto;background:#FFFFFF;border-radius:8px;padding:24px;" +
            "font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#111827;line-height:1.5\">" +
            body + "</div></body></html>";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
