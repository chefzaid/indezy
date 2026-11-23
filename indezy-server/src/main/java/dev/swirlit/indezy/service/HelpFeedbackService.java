package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.HelpFeedbackDay;
import dev.swirlit.indezy.repository.HelpFeedbackDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The help center's **Was this helpful?** (see docs/features.md#help-center-and-legal-pages): each answer counted on its
 * article's day, nothing about who answered; the admin console reads the totals of the last 90 days.
 */
@Service
@RequiredArgsConstructor
public class HelpFeedbackService {

    static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    /** Articles counted a day at most: an article name made up does not grow the table without end. */
    static final int ARTICLES_A_DAY = 100;
    static final int DAYS = 90;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final HelpFeedbackDayRepository days;
    private final Clock clock;

    /** An article's totals: answers saying it helped, and saying it did not. */
    public record Totals(String slug, long helpful, long unhelpful) {
    }

    /** Counts an answer; false (nothing counted) for a name that cannot be an article's, or past the day's articles. */
    @Transactional
    public boolean answer(String slug, boolean helpful) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            return false;
        }
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        HelpFeedbackDay day = days.findBySlugAndDay(slug, today).orElse(null);
        if (day == null) {
            if (days.countByDay(today) >= ARTICLES_A_DAY) {
                return false;
            }
            day = new HelpFeedbackDay();
            day.setSlug(slug);
            day.setDay(today);
        }
        if (helpful) {
            day.setHelpful(day.getHelpful() + 1);
        } else {
            day.setUnhelpful(day.getUnhelpful() + 1);
        }
        try {
            days.saveAndFlush(day);
        } catch (DataIntegrityViolationException e) {
            // Another reader counted the article's first answer of the day at the same moment: this one is lost.
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<Totals> totals() {
        return days.totalsSince(LocalDate.now(clock.withZone(PARIS)).minusDays(DAYS)).stream()
            .map(row -> new Totals((String) row[0], ((Number) row[1]).longValue(), ((Number) row[2]).longValue())).toList();
    }
}
