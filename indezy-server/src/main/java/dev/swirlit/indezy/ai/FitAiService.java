package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.SkillDetail;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.ProjectRepository;
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
 * Weighs an opportunity against the freelancer's profile: how well it fits, its pros and cons, the soft criteria to check
 * (management, team, pace), the questions to ask in the interview, and what to think of its rate against the profile's
 * target and the feed's market median for its main skill.
 */
@Service
@RequiredArgsConstructor
public class FitAiService {

    static final int PRIORITIES_MAX = 1_000;
    private static final int DESCRIPTION_MAX = 6_000;
    private static final int BIO_MAX = 1_500;
    private static final int LIST_MAX = 5;
    private static final int SCORE_MAX = 100;
    private static final long MAX_TOKENS = 6_000;
    private static final String SYSTEM = """
        You advise a French freelance IT consultant on whether a mission suits them. Compare the mission with their profile
        and priorities, honestly and concretely: say what fits, what does not, and what cannot be known from the posting.
        Base every point on the context given, never on assumptions about the client. Write in the language asked, in short
        sentences.""";

    private final AiService ai;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final OfferRateBenchmarkService market;

    /** The analysis of an opportunity. */
    public record Fit(
        @JsonPropertyDescription("How well the mission fits the profile and priorities, from 0 to 100") int score,
        @JsonPropertyDescription("One sentence summing up whether to go for it") String verdict,
        @JsonPropertyDescription("Up to five reasons it fits") List<String> pros,
        @JsonPropertyDescription("Up to five reasons it may not fit, or risks") List<String> cons,
        @JsonPropertyDescription("Up to five soft criteria to check: management style, team spirit, pace, remote culture...")
        List<String> softCriteria,
        @JsonPropertyDescription("Up to five questions to ask the recruiter or the client in the interview") List<String> questions,
        @JsonPropertyDescription("Two sentences on the daily rate against the profile's target and the market median given") String rateAdvice,
        @JsonPropertyDescription("The daily rate to ask in euros, or 0 when the context does not allow a figure") int suggestedRate) {

        public Fit {
            pros = pros == null ? List.of() : List.copyOf(pros);
            cons = cons == null ? List.of() : List.copyOf(cons);
            softCriteria = softCriteria == null ? List.of() : List.copyOf(softCriteria);
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    @Transactional(readOnly = true)
    public Fit weigh(Long userId, Long projectId, DocumentLanguage language, String priorities) {
        Project project = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        Freelance freelance = project.getFreelance();
        User user = freelance == null ? null : users.findByEmail(freelance.getEmail()).orElse(null);
        String description = Objects.toString(project.getDescription(), "");
        String stack = Objects.toString(project.getTechStack(), "");
        String mainSkill = stack.split("[,;/]")[0].strip();
        OfferRateMarketDto median = project.getDailyRate() == null || mainSkill.isEmpty() ? null :
            market.compare(project.getDailyRate(), project.getDailyRate(), null, mainSkill);

        StringBuilder context = new StringBuilder("Answer in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n<mission>\n")
            .append("Role: ").append(Objects.toString(project.getRole(), "")).append('\n')
            .append("Stack: ").append(stack).append('\n')
            .append("Daily rate: ").append(project.getDailyRate() == null ? "unknown" : project.getDailyRate() + " EUR").append('\n')
            .append("Work mode: ").append(Objects.toString(project.getWorkMode(), "unknown"))
            .append(project.getRemoteDaysPerMonth() == null ? "" : ", " + project.getRemoteDaysPerMonth() + " remote days a month").append('\n')
            .append("Place: ").append(Objects.toString(project.getWorkAddress(), project.getClient() == null ? "" :
                Objects.toString(project.getClient().getCity(), ""))).append('\n')
            .append("Start: ").append(Objects.toString(project.getStartDate(), "unknown")).append(", duration: ")
            .append(project.getDurationInMonths() == null ? "unknown" : project.getDurationInMonths() + " months").append('\n')
            .append("Description: ").append(description, 0, Math.min(description.length(), DESCRIPTION_MAX)).append("\n</mission>\n<profile>\n");
        if (user != null) {
            Map<String, SkillDetail> details = user.getSkillDetails();
            context.append("Skills: ").append(user.getSkills().stream().map(skill -> details.containsKey(skill) && details.get(skill).getYears() != null ?
                skill + " (" + details.get(skill).getYears() + " years)" : skill).collect(Collectors.joining(", "))).append('\n')
                .append("Target daily rate: ").append(user.getTargetDailyRate() == null ? "not set" : user.getTargetDailyRate() + " EUR").append('\n')
                .append("Lives in: ").append(Objects.toString(user.getCity(), "")).append('\n');
            String bio = Objects.toString(user.getBio(), "");
            context.append("About: ").append(bio, 0, Math.min(bio.length(), BIO_MAX)).append('\n');
        }
        context.append("</profile>\n");
        if (median != null) {
            context.append("Market: the median daily rate of open offers naming ").append(median.skill()).append(" first is ")
                .append(median.median()).append(" EUR over ").append(median.offers()).append(" offers.\n");
        }
        if (priorities != null && !priorities.isBlank()) {
            String kept = priorities.strip();
            context.append("<priorities>\n").append(kept, 0, Math.min(kept.length(), PRIORITIES_MAX)).append("\n</priorities>\n");
        }
        Fit fit = ai.structured(userId, AiFeature.FIT, SYSTEM, context.toString(), Fit.class, MAX_TOKENS, OutputConfig.Effort.MEDIUM);
        return new Fit(Math.max(0, Math.min(SCORE_MAX, fit.score())), Objects.toString(fit.verdict(), "").strip(), first(fit.pros()),
            first(fit.cons()), first(fit.softCriteria()), first(fit.questions()), Objects.toString(fit.rateAdvice(), "").strip(),
            Math.max(0, fit.suggestedRate()));
    }

    private static List<String> first(List<String> items) {
        return items.stream().filter(item -> item != null && !item.isBlank()).map(String::strip).limit(LIST_MAX).toList();
    }
}
