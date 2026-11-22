package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.OfferRateBenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Prepares the rate negotiation of an opportunity (see docs/features.md#ai-features): a counter-offer, the rate below
 * which to walk away, the ESN's margin when its client rate is known, and the arguments for the call, from the
 * opportunity's rates, the profile's target and the feed's median for its main skill.
 */
@Service
@RequiredArgsConstructor
public class NegotiationAiService {

    private static final int DESCRIPTION_MAX = 4_000;
    private static final int ARGUMENTS_MAX = 5;
    private static final int PERCENT = 100;
    private static final long MAX_TOKENS = 3_000;
    private static final String SYSTEM = """
        You prepare a French freelance IT consultant for a daily rate negotiation with an ESN or a client. From the
        mission, the rates on the table, their target rate and the market median given, propose a counter-offer, the rate
        below which they should walk away, and up to five concrete arguments for the call, each grounded in the context.
        Never invent figures the context does not give. Write in the language asked, in short sentences.""";

    private final AiService ai;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final OfferRateBenchmarkService market;

    /** What the AI answers. */
    public record Answer(
        @JsonPropertyDescription("The daily rate to answer with, in euros") int counterOffer,
        @JsonPropertyDescription("The daily rate below which to decline, in euros") int walkAway,
        @JsonPropertyDescription("One sentence on the strategy for the call") String strategy,
        @JsonPropertyDescription("Up to five arguments for the call") List<String> arguments) {

        public Answer {
            arguments = arguments == null ? List.of() : List.copyOf(arguments.stream().filter(Objects::nonNull).toList());
        }
    }

    /**
     * The preparation: the counter-offer and walk-away rate, the strategy and arguments, the ESN's margin in euros and
     * percent of its client rate (null without both rates), and the market median weighed (0 without one).
     */
    public record Preparation(int counterOffer, int walkAway, String strategy, List<String> arguments, Integer marginEuros,
                              Integer marginPercent, int marketMedian) {

        public Preparation {
            arguments = List.copyOf(arguments);
        }
    }

    @Transactional(readOnly = true)
    public Preparation prepare(Long userId, Long projectId, DocumentLanguage language) {
        Project project = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        Freelance freelance = project.getFreelance();
        User user = freelance == null ? null : users.findByEmail(freelance.getEmail()).orElse(null);
        String stack = Objects.toString(project.getTechStack(), "");
        String mainSkill = stack.split("[,;/]")[0].strip();
        OfferRateMarketDto median = mainSkill.isEmpty() ? null : market.compare(1, 1, null, mainSkill);
        Integer offered = project.getOfferedDailyRate() != null ? project.getOfferedDailyRate() : project.getDailyRate();
        Integer marginEuros = offered != null && project.getClientDailyRate() != null ? project.getClientDailyRate() - offered : null;
        Integer marginPercent = marginEuros != null && project.getClientDailyRate() > 0 ?
            Math.round(marginEuros * (float) PERCENT / project.getClientDailyRate()) : null;
        String description = Objects.toString(project.getDescription(), "");
        StringBuilder context = new StringBuilder("Answer in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n<mission>\n")
            .append("Role: ").append(Objects.toString(project.getRole(), "")).append('\n')
            .append("Stack: ").append(stack).append('\n')
            .append("Through an ESN: ").append(project.getMiddleman() != null ? "yes, " + project.getMiddleman().getCompanyName() : "no").append('\n')
            .append("Stage: ").append(project.getStatus()).append('\n')
            .append("Rate offered: ").append(rate(offered)).append('\n')
            .append("Rate the freelancer asked: ").append(rate(project.getAskedDailyRate())).append('\n')
            .append("Rate the client pays the ESN: ").append(rate(project.getClientDailyRate())).append('\n')
            .append("Duration: ").append(project.getDurationInMonths() == null ? "unknown" : project.getDurationInMonths() + " months").append('\n')
            .append("Description: ").append(description, 0, Math.min(description.length(), DESCRIPTION_MAX)).append("\n</mission>\n")
            .append("Target daily rate: ").append(user == null ? "not set" : rate(user.getTargetDailyRate())).append('\n');
        if (marginPercent != null) {
            context.append("The ESN's margin: ").append(marginEuros).append(" EUR a day, ").append(marginPercent).append(" % of the client rate.\n");
        }
        if (median != null) {
            context.append("Market: the median daily rate of open offers naming ").append(median.skill()).append(" first is ")
                .append(median.median()).append(" EUR over ").append(median.offers()).append(" offers.\n");
        }
        Answer answer = ai.structured(userId, AiFeature.NEGOTIATION, SYSTEM, context.toString(), Answer.class, MAX_TOKENS,
            OutputConfig.Effort.MEDIUM);
        int counter = Math.max(0, answer.counterOffer());
        // Walking away above the counter-offer would make no sense: it is kept at most at it.
        int walkAway = counter > 0 ? Math.min(Math.max(0, answer.walkAway()), counter) : Math.max(0, answer.walkAway());
        return new Preparation(counter, walkAway, Objects.toString(answer.strategy(), "").strip(),
            answer.arguments().stream().filter(argument -> !argument.isBlank()).map(String::strip).limit(ARGUMENTS_MAX).toList(),
            marginEuros, marginPercent, median == null ? 0 : median.median());
    }

    private static String rate(Integer rate) {
        return rate == null ? "unknown" : rate + " EUR";
    }
}
