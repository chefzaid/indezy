package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Writes the weekly digest, in HTML and plain text, in the account's language: where the pipeline stands, the week's
 * interviews, the follow-ups due, the opportunities gone quiet, the missions ending and the new offers matching the
 * profile. Sections with nothing to say are left out. It carries a one-click unsubscribe (RFC 8058) and no tracking.
 */
@Component
public class WeeklyDigestComposer {

    public static final int ITEMS = 5;
    private static final String ACCENT = "#4F46E5";
    private static final String MUTED = "#6B7280";

    private final MailProperties properties;

    public WeeklyDigestComposer(MailProperties properties) {
        this.properties = properties;
    }

    /** What the dashboard says of the week, copied out of it. */
    public record Week(Map<String, Long> projectsByStatus, List<DashboardStatsDto.UpcomingInterview> interviews,
                       List<DashboardStatsDto.FollowUpDue> followUps, List<DashboardStatsDto.StaleOpportunity> stale,
                       List<DashboardStatsDto.MissionEndingSoon> ending) {

        public Week {
            projectsByStatus = Map.copyOf(projectsByStatus);
            interviews = List.copyOf(interviews);
            followUps = List.copyOf(followUps);
            stale = List.copyOf(stale);
            ending = List.copyOf(ending);
        }

        public static Week of(DashboardStatsDto stats) {
            return new Week(stats.getProjectsByStatus() == null ? Map.of() : stats.getProjectsByStatus(), orNone(stats.getUpcomingInterviews()),
                orNone(stats.getFollowUpsDue()), orNone(stats.getStaleOpportunities()), orNone(stats.getMissionsEndingSoon()));
        }

        /** Opportunities in the pipeline: first contact, interviewing or with an offer. */
        long open() {
            return projectsByStatus.getOrDefault("CONTACT", 0L) + projectsByStatus.getOrDefault("INTERVIEW", 0L) +
                projectsByStatus.getOrDefault("OFFER", 0L);
        }

        private static <T> List<T> orNone(List<T> items) {
            return items == null ? List.of() : items;
        }
    }

    /** One account's week: its dashboard, the matching offers found in the last 7 days and how many there are. */
    public record Digest(Long userId, String firstName, String recipient, DocumentLanguage language, String token, LocalDate week,
                         Week stats, List<OfferDto> offers, long offersFound) {

        public Digest {
            offers = List.copyOf(offers);
        }

        /** Whether it has anything to say: a digest of an empty week is not sent. */
        public boolean isEmpty() {
            return stats.interviews().isEmpty() && stats.followUps().isEmpty() && stats.stale().isEmpty() && stats.ending().isEmpty() &&
                offersFound == 0 && stats.open() == 0;
        }

        @Override
        public String toString() {
            return "Digest[" + userId + ", " + week + "]";
        }
    }

    public Email compose(Digest digest) {
        DocumentLanguage language = digest.language();
        DateTimeFormatter day = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(WeeklyDigestLabel.locale(language));
        DateTimeFormatter moment = DateTimeFormatter.ofPattern(language == DocumentLanguage.EN ? "EEE d MMM, HH:mm" : "EEE d MMM, HH'h'mm",
            WeeklyDigestLabel.locale(language));
        String heading = WeeklyDigestLabel.HEADING.format(language, digest.firstName());
        StringBuilder html = new StringBuilder("<h1 style=\"font-size:20px;margin:0 0 16px\">").append(escape(heading)).append("</h1>");
        StringBuilder text = new StringBuilder(heading).append("\n\n");
        Week stats = digest.stats();

        Map<String, Long> status = stats.projectsByStatus();
        if (stats.open() > 0) {
            section(html, text, WeeklyDigestLabel.PIPELINE.in(language), List.of(WeeklyDigestLabel.PIPELINE_LINE.format(language, stats.open(),
                status.getOrDefault("CONTACT", 0L), status.getOrDefault("INTERVIEW", 0L), status.getOrDefault("OFFER", 0L))), item -> null);
        }
        section(html, text, WeeklyDigestLabel.INTERVIEWS.in(language), stats.interviews().stream().limit(ITEMS)
            .map(interview -> interview.getDate().format(moment) + " · " + interview.getTitle() + " · " + who(interview.getRole(),
                interview.getClientName())).toList(), item -> null);
        section(html, text, WeeklyDigestLabel.FOLLOW_UPS.in(language), stats.followUps().stream().limit(ITEMS)
            .map(followUp -> WeeklyDigestLabel.FOLLOW_UP_LINE.format(language, who(followUp.getName(), followUp.getClientName()),
                followUp.getDaysWaiting())).toList(), item -> null);
        section(html, text, WeeklyDigestLabel.STALE.in(language), stats.stale().stream().limit(ITEMS)
            .map(stale -> WeeklyDigestLabel.STALE_LINE.format(language, who(stale.getRole(), stale.getClientName()),
                stale.getDaysSinceActivity())).toList(), item -> null);
        section(html, text, WeeklyDigestLabel.ENDING.in(language), stats.ending().stream().limit(ITEMS)
            .map(ending -> WeeklyDigestLabel.ENDING_LINE.format(language, who(ending.getRole(), ending.getClientName()),
                ending.getEndDate().format(day))).toList(), item -> null);
        if (digest.offersFound() > 0) {
            String title = digest.offersFound() == 1 ? WeeklyDigestLabel.OFFERS_ONE.in(language) :
                WeeklyDigestLabel.OFFERS_MANY.format(language, digest.offersFound());
            List<OfferDto> offers = digest.offers().stream().limit(ITEMS).toList();
            section(html, text, title, offers.stream().map(offer -> offer.title() + offerFacts(offer, language)).toList(),
                line -> properties.appUrl() + "/offers/" + offers.get(line).id());
            long more = digest.offersFound() - offers.size();
            if (more > 0) {
                html.append("<p style=\"color:").append(MUTED).append(";margin:0 0 16px\">")
                    .append(escape(WeeklyDigestLabel.MORE.format(language, more))).append("</p>");
                text.append(WeeklyDigestLabel.MORE.format(language, more)).append("\n\n");
            }
        }

        String app = properties.appUrl() + "/dashboard";
        html.append("<p style=\"margin:24px 0\"><a href=\"").append(escape(app)).append("\" style=\"color:").append(ACCENT).append("\">")
            .append(escape(WeeklyDigestLabel.OPEN.in(language))).append("</a></p>");
        text.append(WeeklyDigestLabel.OPEN.in(language)).append(": ").append(app).append("\n\n");
        String settings = properties.appUrl() + "/profile";
        html.append("<p style=\"color:").append(MUTED).append(";font-size:12px;border-top:1px solid #E5E7EB;padding-top:16px\">")
            .append(escape(WeeklyDigestLabel.WHY.in(language))).append("<br><a href=\"").append(escape(settings)).append("\" style=\"color:")
            .append(MUTED).append("\">").append(escape(WeeklyDigestLabel.CHANGE.in(language))).append("</a></p>");
        text.append("--\n").append(WeeklyDigestLabel.WHY.in(language)).append('\n').append(WeeklyDigestLabel.CHANGE.in(language))
            .append(": ").append(settings).append('\n');

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe", "<" + properties.appUrl() + "/api/public/weekly-digest/unsubscribe/" + digest.token() + ">");
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        return new Email(digest.recipient(), WeeklyDigestLabel.SUBJECT.format(language, digest.week().format(day)),
            SavedSearchEmailComposer.page(html), text.toString(), headers, "weekly-digest/" + digest.userId() + "/" + digest.week());
    }

    private static String who(String what, String client) {
        return client == null || client.isBlank() ? what : what + " (" + client + ")";
    }

    private static String offerFacts(OfferDto offer, DocumentLanguage language) {
        List<String> facts = SavedSearchEmailComposer.facts(offer, language);
        return facts.isEmpty() ? "" : " · " + String.join(" · ", facts);
    }

    /** A titled list; {@code links} gives the address of a line by its index, or null for none. */
    private static void section(StringBuilder html, StringBuilder text, String title, List<String> lines, Function<Integer, String> links) {
        if (lines.isEmpty()) {
            return;
        }
        html.append("<h2 style=\"font-size:16px;margin:24px 0 8px\">").append(escape(title)).append("</h2><ul style=\"margin:0;padding-left:20px\">");
        text.append(title).append('\n');
        List<String> plain = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String link = links.apply(i);
            html.append("<li style=\"margin:0 0 6px\">");
            if (link == null) {
                html.append(escape(lines.get(i)));
            } else {
                html.append("<a href=\"").append(escape(link)).append("\" style=\"color:").append(ACCENT).append("\">")
                    .append(escape(lines.get(i))).append("</a>");
            }
            html.append("</li>");
            plain.add("- " + lines.get(i) + (link == null ? "" : "\n  " + link));
        }
        html.append("</ul>");
        text.append(String.join("\n", plain)).append("\n\n");
    }
}
