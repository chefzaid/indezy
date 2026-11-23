package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.repository.ProjectNoteRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The workspace's opportunities and journal notes searched by what a question means rather than by its words (see
 * docs/features.md#ai-features): the latest opportunities and notes, each shortened, are given to the AI, which answers
 * the ones that fit with why; only ids it was given are kept.
 */
@Service
@RequiredArgsConstructor
public class SemanticSearchAiService {

    public static final int QUERY_MAX = 300;
    static final int OPPORTUNITIES_MAX = 200;
    static final int NOTES_MAX = 300;
    static final int HITS_MAX = 10;
    private static final int TEXT_CHARS = 240;
    private static final long MAX_TOKENS = 2_000;
    private static final String SYSTEM = """
        You search a French freelance IT consultant's own records by meaning. You get a question and a list of records,
        each with an id such as O12 (an opportunity) or N7 (a note on an opportunity). Answer the records that fit the
        question best, the best first, at most ten, each with one short sentence saying why, in the question's language.
        Answer only ids from the list; none when nothing fits.""";

    private final AiService ai;
    private final ProjectRepository projects;
    private final ProjectNoteRepository notes;

    /** What the AI answers. */
    public record Reading(@JsonPropertyDescription("The records that fit, the best first") List<Found> found) {

        public Reading {
            found = found == null ? List.of() : List.copyOf(found.stream().filter(Objects::nonNull).toList());
        }
    }

    /** One record found. */
    public record Found(@JsonPropertyDescription("The record's id, as O12 or N7") String id,
                        @JsonPropertyDescription("One short sentence saying why it fits") String why) {
    }

    /** A record found: an opportunity or a note, the opportunity it opens, what it says, and why it fits. */
    public record Hit(String kind, Long projectId, Long noteId, String title, String excerpt, String why) {
    }

    @Transactional(readOnly = true)
    public List<Hit> search(Long userId, Long freelanceId, String query) {
        Map<String, Hit> records = new LinkedHashMap<>();
        StringBuilder list = new StringBuilder();
        projects.findByFreelanceId(freelanceId).stream()
            .sorted(Comparator.comparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(OPPORTUNITIES_MAX).forEach(project -> {
                String id = "O" + project.getId();
                String title = title(project);
                String text = shorten(Stream.of(project.getTechStack(), project.getDescription(), project.getNotes(), project.getEndReason())
                    .filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" · ")));
                records.put(id, new Hit("OPPORTUNITY", project.getId(), null, title, text, null));
                list.append(id).append(" | ").append(title).append(" | ").append(project.getStatus()).append(" | ").append(text).append('\n');
            });
        notes.findByFreelanceId(freelanceId).stream().limit(NOTES_MAX).forEach(note -> {
            String id = "N" + note.getId();
            String text = shorten(note.getContent());
            records.put(id, new Hit("NOTE", note.getProject().getId(), note.getId(), title(note.getProject()), text, null));
            list.append(id).append(" | on ").append(title(note.getProject())).append(" | ").append(text).append('\n');
        });
        if (records.isEmpty()) {
            return List.of();
        }
        String text = "<question>\n" + query.strip() + "\n</question>\n<records>\n" + list + "</records>";
        Reading reading = ai.structured(userId, AiFeature.SEARCH, SYSTEM, text, Reading.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        return hits(reading, records);
    }

    /** The records the AI found, those it was given only, once each, ten at most. */
    static List<Hit> hits(Reading reading, Map<String, Hit> records) {
        Map<String, Hit> kept = new LinkedHashMap<>();
        for (Found found : reading.found()) {
            String id = found.id() == null ? "" : found.id().strip().toUpperCase(java.util.Locale.ROOT);
            Hit record = records.get(id);
            if (record != null && !kept.containsKey(id) && kept.size() < HITS_MAX) {
                kept.put(id, new Hit(record.kind(), record.projectId(), record.noteId(), record.title(), record.excerpt(),
                    found.why() == null ? null : found.why().strip()));
            }
        }
        return List.copyOf(kept.values());
    }

    private static String title(Project project) {
        String client = project.getClient() == null ? null : project.getClient().getCompanyName();
        return client == null ? project.getRole() : project.getRole() + " · " + client;
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= TEXT_CHARS ? flat : flat.substring(0, TEXT_CHARS - 1) + "…";
    }
}
