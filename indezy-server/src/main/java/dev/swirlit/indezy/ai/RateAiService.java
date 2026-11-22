package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.model.SkillDetail;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.OfferRateBenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Suggests the daily rate to ask for an opportunity being written (see docs/features.md#ai-features): from what the
 * form says of it, the profile's skills and years, target rate and town, and the feed's median for its main skill.
 */
@Service
@RequiredArgsConstructor
public class RateAiService {

    static final int TEXT_MAX = 4_000;
    static final int RATE_MIN = 100;
    static final int RATE_MAX = 3_000;
    private static final int REASONS_MAX = 3;
    private static final long MAX_TOKENS = 2_000;
    private static final String SYSTEM = """
        You suggest the daily rate in euros, excluding VAT, that a French freelance IT consultant should ask for a mission.
        Weigh the mission (role, stack, work mode, place, duration) against their skills and years and their target rate,
        and use the market median given when there is one. Give a rate and a range a client would accept, and up to three
        short reasons, each grounded in the context given. Write in the language asked.""";

    private final AiService ai;
    private final UserRepository users;
    private final OfferRateBenchmarkService market;

    /** What the form says of the opportunity. */
    public record Mission(String role, String techStack, WorkMode workMode, Integer remoteDaysPerMonth, String place, Integer durationInMonths,
                          String description) {

        // Spring MVC logs bodies through toString at DEBUG: never the description.
        @Override
        public String toString() {
            return "Mission[workMode=" + workMode + "]";
        }
    }

    /** What the AI answers. */
    public record Answer(
        @JsonPropertyDescription("The daily rate to ask, in euros") int rate,
        @JsonPropertyDescription("The lowest daily rate worth accepting, in euros") int low,
        @JsonPropertyDescription("The highest daily rate a client would likely accept, in euros") int high,
        @JsonPropertyDescription("Up to three short reasons for the rate") List<String> reasons) {

        public Answer {
            reasons = reasons == null ? List.of() : List.copyOf(reasons.stream().filter(Objects::nonNull).toList());
        }
    }

    /** The rate to ask, the range around it, why, and the market median it was weighed against (0 without one). */
    public record Suggestion(int rate, int low, int high, List<String> reasons, int marketMedian) {

        public Suggestion {
            reasons = List.copyOf(reasons);
        }
    }

    @Transactional(readOnly = true)
    public Suggestion suggest(Long userId, Mission mission, DocumentLanguage language) {
        User user = users.findById(userId).orElse(null);
        String stack = Objects.toString(mission.techStack(), "");
        String mainSkill = stack.split("[,;/]")[0].strip();
        // Any rate gives the median: only the median is used.
        OfferRateMarketDto median = mainSkill.isEmpty() ? null : market.compare(1, 1, null, mainSkill);
        Answer answer = ai.structured(userId, AiFeature.RATE, SYSTEM, context(mission, user, median, language), Answer.class, MAX_TOKENS,
            OutputConfig.Effort.LOW);
        return clean(answer, median == null ? 0 : median.median());
    }

    static String context(Mission mission, User user, OfferRateMarketDto median, DocumentLanguage language) {
        String description = Objects.toString(mission.description(), "").strip();
        StringBuilder context = new StringBuilder("Answer in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n<mission>\n")
            .append("Role: ").append(Objects.toString(mission.role(), "")).append('\n')
            .append("Stack: ").append(Objects.toString(mission.techStack(), "")).append('\n')
            .append("Work mode: ").append(Objects.toString(mission.workMode(), "unknown"))
            .append(mission.remoteDaysPerMonth() == null ? "" : ", " + mission.remoteDaysPerMonth() + " remote days a month").append('\n')
            .append("Place: ").append(Objects.toString(mission.place(), "unknown")).append('\n')
            .append("Duration: ").append(mission.durationInMonths() == null ? "unknown" : mission.durationInMonths() + " months").append('\n')
            .append("Description: ").append(description, 0, Math.min(description.length(), TEXT_MAX)).append("\n</mission>\n");
        if (user != null) {
            Map<String, SkillDetail> details = user.getSkillDetails();
            context.append("<profile>\nSkills: ").append(user.getSkills().stream().map(skill -> details.containsKey(skill) &&
                    details.get(skill).getYears() != null ? skill + " (" + details.get(skill).getYears() + " years)" : skill)
                    .collect(Collectors.joining(", "))).append('\n')
                .append("Target daily rate: ").append(user.getTargetDailyRate() == null ? "not set" : user.getTargetDailyRate() + " EUR").append('\n')
                .append("Lives in: ").append(Objects.toString(user.getCity(), "")).append("\n</profile>\n");
        }
        if (median != null) {
            context.append("Market: the median daily rate of open offers naming ").append(median.skill()).append(" first is ")
                .append(median.median()).append(" EUR over ").append(median.offers()).append(" offers.\n");
        }
        return context.toString();
    }

    /** The answer kept within sensible rates, its range around the rate, blank reasons dropped. */
    static Suggestion clean(Answer answer, int marketMedian) {
        int rate = Math.clamp(answer.rate(), RATE_MIN, RATE_MAX);
        int low = answer.low() <= 0 ? rate : Math.clamp(answer.low(), RATE_MIN, rate);
        int high = answer.high() <= 0 ? rate : Math.clamp(answer.high(), rate, RATE_MAX);
        List<String> reasons = answer.reasons().stream().filter(reason -> reason != null && !reason.isBlank()).map(String::strip)
            .limit(REASONS_MAX).toList();
        return new Suggestion(rate, low, high, reasons, marketMedian);
    }
}
