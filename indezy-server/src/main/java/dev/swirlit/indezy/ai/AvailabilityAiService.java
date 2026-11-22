package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.SkillDetail;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Drafts the announcement of the freelancer's next availability (see docs/features.md#ai-features): a LinkedIn post and
 * a shorter post for the member network, from the profile and the day after the running mission ends (or the
 * profile's availability date). Nothing is posted: the freelancer copies them or keeps the network one as a draft.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityAiService {

    static final int NETWORK_MAX = 3_000;
    private static final int BIO_MAX = 1_000;
    private static final long MAX_TOKENS = 2_000;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final String SYSTEM = """
        You write the posts a French freelance IT consultant uses to announce they will be available for a new mission:
        one for LinkedIn (120 to 200 words, a hook line, what they do and with what, the kind of mission they look for,
        a call to get in touch, three to five hashtags) and one for a freelancers' network (under 80 words, plain, no
        hashtags). Write in the first person, in the language asked, from the profile only: never invent clients,
        figures, results or skills.""";

    private final AiService ai;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ProjectRepository projects;
    private final Clock clock;

    /** The two posts, and the day they announce. */
    public record Announcement(String linkedin, String network, LocalDate availableFrom) {
    }

    /** What the AI answers. */
    public record Answer(
        @JsonPropertyDescription("The LinkedIn post, paragraphs separated by blank lines, hashtags on the last line") String linkedin,
        @JsonPropertyDescription("The network post, under 80 words") String network) {
    }

    @Transactional(readOnly = true)
    public Announcement draft(Long userId, Long freelanceId, DocumentLanguage language) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Freelance freelance = freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        LocalDate from = availableFrom(projects.findByFreelanceId(freelanceId), freelance.getAvailabilityDate(), today);
        Answer answer = ai.structured(userId, AiFeature.MESSAGE, SYSTEM, context(user, from, language), Answer.class, MAX_TOKENS,
            OutputConfig.Effort.LOW);
        String network = Objects.toString(answer.network(), "").strip();
        return new Announcement(Objects.toString(answer.linkedin(), "").strip(),
            network.length() > NETWORK_MAX ? network.substring(0, NETWORK_MAX) : network, from);
    }

    /** The day after the running mission ending last, else the profile's availability date, else today. */
    static LocalDate availableFrom(List<Project> projects, LocalDate profileDate, LocalDate today) {
        return projects.stream()
            .filter(project -> project.getStatus() == ProjectStatus.WON && project.getArchivedAt() == null)
            .map(Project::lastDay).filter(Objects::nonNull).filter(last -> !last.isBefore(today))
            .max(Comparator.naturalOrder()).map(last -> last.plusDays(1))
            .orElse(profileDate != null && profileDate.isAfter(today) ? profileDate : today);
    }

    static String context(User user, LocalDate from, DocumentLanguage language) {
        Map<String, SkillDetail> details = user.getSkillDetails();
        String bio = Objects.toString(user.getBio(), "").strip();
        return "Answer in " + (language == DocumentLanguage.EN ? "English" : "French") + ".\n<profile>\n" +
            "Available from: " + from + "\n" +
            "Position: " + Objects.toString(user.getPosition(), "") + "\n" +
            "Skills: " + user.getSkills().stream().map(skill -> details.containsKey(skill) && details.get(skill).getYears() != null ?
                skill + " (" + details.get(skill).getYears() + " years)" : skill).collect(Collectors.joining(", ")) + "\n" +
            "Based in: " + Objects.toString(user.getCity(), "") + "\n" +
            "About: " + bio.substring(0, Math.min(bio.length(), BIO_MAX)) + "\n</profile>\n";
    }
}
