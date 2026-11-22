package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.ProjectChatMessage;
import dev.swirlit.indezy.model.ProjectNote;
import dev.swirlit.indezy.repository.ProjectChatMessageRepository;
import dev.swirlit.indezy.repository.ProjectNoteRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The AI conversation about an opportunity: its description, notes, steps and journal are the context, for interview
 * preparation, negotiation angles or a post-mortem; the history is kept on the opportunity and goes with it.
 */
@Service
@RequiredArgsConstructor
public class ChatAiService {

    static final int QUESTION_MAX = 2_000;
    /** The turns sent back with each question: the latest, enough for a session of preparation. */
    static final int HISTORY = 20;
    private static final int DESCRIPTION_MAX = 6_000;
    private static final int NOTES_MAX = 3_000;
    private static final int JOURNAL = 10;
    private static final long MAX_TOKENS = 4_000;
    private static final String SYSTEM = """
        You are the coach of a French freelance IT consultant for one mission they are considering or doing: interview
        preparation, negotiation, client relationship, or what to learn once it ended. Use the mission's context below;
        say when something is not in it rather than guessing. Answer in the language of the question, concisely, with
        concrete steps or wording the freelancer can use.""";

    private final AiService ai;
    private final ProjectRepository projects;
    private final ProjectNoteRepository notes;
    private final ProjectChatMessageRepository chat;

    /** A turn as the page shows it. */
    public record Turn(Long id, boolean fromMember, String text, LocalDateTime createdAt) {
    }

    @Transactional(readOnly = true)
    public List<Turn> history(Long projectId) {
        return chat.findByProjectIdOrderByIdAsc(projectId).stream().map(ChatAiService::toDto).toList();
    }

    /** Asks a question about the opportunity: kept with the answer, unless the AI cannot answer. */
    @Transactional
    public List<Turn> ask(Long userId, Long projectId, String question) {
        Project project = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        String asked = question.strip();
        if (asked.isEmpty() || asked.length() > QUESTION_MAX) {
            throw new IllegalArgumentException("A question of 1 to " + QUESTION_MAX + " characters");
        }
        List<ProjectChatMessage> before = chat.findByProjectIdOrderByIdAsc(projectId);
        List<AiClient.Turn> turns = new ArrayList<>(before.subList(Math.max(0, before.size() - HISTORY), before.size()).stream()
            .map(turn -> new AiClient.Turn(turn.isFromMember(), turn.getText())).toList());
        // The conversation starts with the member: an answer left first goes.
        while (!turns.isEmpty() && !turns.getFirst().fromMember()) {
            turns.removeFirst();
        }
        turns.add(new AiClient.Turn(true, asked));
        String answer = ai.chat(userId, AiFeature.CHAT, SYSTEM + "\n\n" + context(project), turns, MAX_TOKENS, OutputConfig.Effort.MEDIUM);
        save(projectId, true, asked);
        save(projectId, false, answer.length() > ProjectChatMessage.TEXT_MAX ? answer.substring(0, ProjectChatMessage.TEXT_MAX) : answer);
        return history(projectId);
    }

    /** A turn in the account's data export, with its opportunity. */
    public record Exported(Long projectId, boolean fromMember, String text, LocalDateTime createdAt) {
    }

    /** Every AI conversation of the workspace's opportunities, for the account's data export. */
    @Transactional(readOnly = true)
    public List<Exported> export(Long freelanceId) {
        List<Long> ids = projects.findByFreelanceId(freelanceId).stream().map(Project::getId).toList();
        return ids.isEmpty() ? List.of() : chat.findByProjectIdInOrderByIdAsc(ids).stream()
            .map(turn -> new Exported(turn.getProjectId(), turn.isFromMember(), turn.getText(), turn.getCreatedAt())).toList();
    }

    @Transactional
    public void clear(Long projectId) {
        chat.deleteByProjectId(projectId);
    }

    private String context(Project project) {
        String description = Objects.toString(project.getDescription(), "");
        String own = Objects.toString(project.getNotes(), "");
        StringBuilder context = new StringBuilder("<mission>\n")
            .append("Role: ").append(Objects.toString(project.getRole(), "")).append('\n')
            .append("Client: ").append(project.getClient() == null ? "" : project.getClient().getCompanyName()).append('\n')
            .append("Through: ").append(project.getMiddleman() == null ? "direct" : project.getMiddleman().getCompanyName()).append('\n')
            .append("Stage: ").append(project.getStatus()).append('\n')
            .append("Stack: ").append(Objects.toString(project.getTechStack(), "")).append('\n')
            .append("Daily rate: ").append(project.getDailyRate() == null ? "unknown" : project.getDailyRate() + " EUR").append('\n')
            .append("Work mode: ").append(Objects.toString(project.getWorkMode(), "unknown")).append('\n')
            .append("Start: ").append(Objects.toString(project.getStartDate(), "unknown")).append(", duration: ")
            .append(project.getDurationInMonths() == null ? "unknown" : project.getDurationInMonths() + " months").append('\n')
            .append("Description: ").append(description, 0, Math.min(description.length(), DESCRIPTION_MAX)).append('\n')
            .append("Freelancer's notes: ").append(own, 0, Math.min(own.length(), NOTES_MAX)).append('\n');
        for (InterviewStep step : project.getSteps()) {
            context.append("Step: ").append(step.getTitle()).append(" (").append(step.getStatus())
                .append(step.getDate() == null ? "" : ", " + step.getDate().toLocalDate()).append(')')
                .append(step.getNotes() == null || step.getNotes().isBlank() ? "" : ": " + step.getNotes().strip()).append('\n');
        }
        for (ProjectNote note : notes.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream().limit(JOURNAL).toList()) {
            context.append("Journal: ").append(Objects.toString(note.getContent(), "").strip()).append('\n');
        }
        return context.append("</mission>").toString();
    }

    private void save(Long projectId, boolean fromMember, String text) {
        ProjectChatMessage message = new ProjectChatMessage();
        message.setProjectId(projectId);
        message.setFromMember(fromMember);
        message.setText(text);
        chat.save(message);
    }

    private static Turn toDto(ProjectChatMessage message) {
        return new Turn(message.getId(), message.isFromMember(), message.getText(), message.getCreatedAt());
    }
}
