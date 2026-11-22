package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.SkillDetail;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Prepares the next interview of an opportunity (see docs/features.md#ai-features): the technical and behavioural
 * questions it will likely ask, each with a hint drawn from the profile, a two-minute pitch for this mission, and the
 * questions to ask, from the opportunity, its steps so far and the profile.
 */
@Service
@RequiredArgsConstructor
public class InterviewAiService {

    private static final int DESCRIPTION_MAX = 5_000;
    private static final int NOTES_MAX = 600;
    private static final int STEPS_MAX = 10;
    private static final int LIST_MAX = 6;
    private static final long MAX_TOKENS = 6_000;
    private static final String SYSTEM = """
        You coach a French freelance IT consultant for their next interview for a mission. From the mission, the steps so
        far and their profile, list the technical questions it will likely ask and the behavioural ones, each with a short
        hint on how to answer from the profile's own experience, write a two-minute pitch for this mission in the first
        person, and the questions they should ask. Never invent experience the profile does not state. Write in the
        language asked.""";

    private final AiService ai;
    private final ProjectRepository projects;
    private final InterviewStepRepository steps;
    private final UserRepository users;

    /** A question likely asked, and how to answer it. */
    public record Question(
        @JsonPropertyDescription("The question as the interviewer would ask it") String question,
        @JsonPropertyDescription("One or two sentences on how to answer, from the profile") String hint) {
    }

    /** The preparation. */
    public record Preparation(
        @JsonPropertyDescription("Up to six technical questions likely asked") List<Question> technical,
        @JsonPropertyDescription("Up to six behavioural questions likely asked") List<Question> behavioural,
        @JsonPropertyDescription("A two-minute pitch for this mission, in the first person, about 250 words") String pitch,
        @JsonPropertyDescription("Up to six questions to ask the interviewer") List<String> toAsk) {

        public Preparation {
            technical = technical == null ? List.of() : List.copyOf(technical.stream().filter(Objects::nonNull).toList());
            behavioural = behavioural == null ? List.of() : List.copyOf(behavioural.stream().filter(Objects::nonNull).toList());
            toAsk = toAsk == null ? List.of() : List.copyOf(toAsk.stream().filter(Objects::nonNull).toList());
        }
    }

    @Transactional(readOnly = true)
    public Preparation prepare(Long userId, Long projectId, DocumentLanguage language) {
        Project project = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        Freelance freelance = project.getFreelance();
        User user = freelance == null ? null : users.findByEmail(freelance.getEmail()).orElse(null);
        String description = Objects.toString(project.getDescription(), "");
        StringBuilder context = new StringBuilder("Answer in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n<mission>\n")
            .append("Role: ").append(Objects.toString(project.getRole(), "")).append('\n')
            .append("Company: ").append(project.getClient() == null ? "" : project.getClient().getCompanyName())
            .append(project.getMiddleman() == null ? "" : ", through the ESN " + project.getMiddleman().getCompanyName()).append('\n')
            .append("Stack: ").append(Objects.toString(project.getTechStack(), "")).append('\n')
            .append("Description: ").append(description, 0, Math.min(description.length(), DESCRIPTION_MAX)).append("\n</mission>\n<steps>\n");
        steps.findByProjectIdOrderByDate(projectId).stream().limit(STEPS_MAX).forEach(step -> context.append("- ").append(line(step)).append('\n'));
        context.append("</steps>\n");
        if (user != null) {
            Map<String, SkillDetail> details = user.getSkillDetails();
            String bio = Objects.toString(user.getBio(), "");
            context.append("<profile>\nPosition: ").append(Objects.toString(user.getPosition(), "")).append('\n')
                .append("Skills: ").append(user.getSkills().stream().map(skill -> details.containsKey(skill) && details.get(skill).getYears() != null ?
                    skill + " (" + details.get(skill).getYears() + " years)" : skill).collect(Collectors.joining(", "))).append('\n')
                .append("About: ").append(bio, 0, Math.min(bio.length(), DESCRIPTION_MAX)).append("\n</profile>\n");
        }
        Preparation answer = ai.structured(userId, AiFeature.INTERVIEW, SYSTEM, context.toString(), Preparation.class, MAX_TOKENS,
            OutputConfig.Effort.MEDIUM);
        return new Preparation(questions(answer.technical()), questions(answer.behavioural()), Objects.toString(answer.pitch(), "").strip(),
            answer.toAsk().stream().filter(question -> !question.isBlank()).map(String::strip).limit(LIST_MAX).toList());
    }

    private static String line(InterviewStep step) {
        String notes = Objects.toString(step.getNotes(), "").strip();
        return Objects.toString(step.getTitle(), "") + " (" + step.getStatus() + (step.getDate() == null ? "" : ", " + step.getDate().toLocalDate()) + ")" +
            (notes.isEmpty() ? "" : ": " + notes.substring(0, Math.min(notes.length(), NOTES_MAX)));
    }

    private static List<Question> questions(List<Question> questions) {
        return questions.stream().filter(question -> question.question() != null && !question.question().isBlank())
            .map(question -> new Question(question.question().strip(), Objects.toString(question.hint(), "").strip())).limit(LIST_MAX).toList();
    }
}
