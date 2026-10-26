package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.GlobalSearchResultDto;
import dev.swirlit.indezy.dto.GlobalSearchResultDto.Hit;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.ProjectNote;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.ProjectNoteRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Searches a workspace's opportunities, clients, contacts and journal notes at once, accents and case aside, for the
 * search opened from anywhere in the app. A record matches when one of its fields contains every word typed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalSearchService {

    static final int MIN_QUERY = 2;
    static final int MAX_QUERY = 100;
    static final int HITS_PER_KIND = 8;
    private static final int EXCERPT_BEFORE = 40;
    private static final int EXCERPT_LENGTH = 140;

    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final ContactRepository contacts;
    private final ProjectNoteRepository notes;

    public GlobalSearchResultDto search(Long freelanceId, String query) {
        List<String> words = words(query);
        String typed = query == null ? "" : query.strip();
        if (typed.length() < MIN_QUERY || typed.length() > MAX_QUERY || words.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Search for %d to %d characters", MIN_QUERY, MAX_QUERY));
        }
        List<Hit> projectHits = projects.findByFreelanceId(freelanceId).stream()
            .sorted(Comparator.comparing(Project::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(project -> hit(words, project.getId(), project.getRole(), clientName(project.getClient()),
                project.getRole(), clientName(project.getClient()), project.getTechStack(), project.getDescription(),
                project.getNotes()))
            .filter(Objects::nonNull).limit(HITS_PER_KIND).toList();
        List<Hit> clientHits = clients.findByFreelanceId(freelanceId).stream()
            .sorted(Comparator.comparing(Client::getCompanyName, String.CASE_INSENSITIVE_ORDER))
            .map(client -> hit(words, client.getId(), client.getCompanyName(), client.getCity(),
                client.getCompanyName(), client.getCity(), client.getDomain(), client.getNotes()))
            .filter(Objects::nonNull).limit(HITS_PER_KIND).toList();
        List<Hit> contactHits = contacts.findByFreelanceId(freelanceId).stream()
            .sorted(Comparator.comparing(Contact::getFullName, String.CASE_INSENSITIVE_ORDER))
            .map(contact -> hit(words, contact.getId(), contact.getFullName(), clientName(contact.getClient()),
                contact.getFullName(), clientName(contact.getClient()), contact.getEmail(), contact.getPhone(), contact.getNotes()))
            .filter(Objects::nonNull).limit(HITS_PER_KIND).toList();
        List<Hit> noteHits = notes.findByFreelanceId(freelanceId).stream()
            .map(note -> noteHit(words, note))
            .filter(Objects::nonNull).limit(HITS_PER_KIND).toList();
        return new GlobalSearchResultDto(projectHits, clientHits, contactHits, noteHits);
    }

    /**
     * The record as a hit when one of {@code fields} contains every word: the first two are its title and subtitle, the
     * passage is shown when the words were found in another field only.
     */
    private static Hit hit(List<String> words, Long id, String title, String subtitle, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (containsAll(fields[i], words)) {
                String excerpt = i < 2 ? null : excerpt(fields[i], words.getFirst());
                return new Hit(id, title, subtitle, excerpt, null);
            }
        }
        return null;
    }

    private static Hit noteHit(List<String> words, ProjectNote note) {
        if (!containsAll(note.getContent(), words)) {
            return null;
        }
        Project project = note.getProject();
        return new Hit(note.getId(), project.getRole(), clientName(project.getClient()), excerpt(note.getContent(), words.getFirst()),
            project.getId());
    }

    /** The words typed, folded, longest first so the excerpt centres on the most telling one. */
    static List<String> words(String query) {
        String folded = TextFolding.fold(query == null ? "" : query);
        return Stream.of(folded.split("\\s+")).filter(word -> !word.isEmpty()).distinct()
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    static boolean containsAll(String field, List<String> words) {
        String folded = TextFolding.fold(field);
        return folded != null && words.stream().allMatch(folded::contains);
    }

    /** About {@value #EXCERPT_LENGTH} characters of {@code text} around {@code word}, on one line, cut at spaces. */
    static String excerpt(String text, String word) {
        String line = text.replaceAll("\\s+", " ").strip();
        String folded = TextFolding.fold(line);
        // Folding keeps the length of accented letters; should it not, the passage starts at the beginning.
        int at = folded.length() == line.length() ? Math.max(folded.indexOf(word), 0) : 0;
        int start = Math.max(0, at - EXCERPT_BEFORE);
        int end = Math.min(line.length(), start + EXCERPT_LENGTH);
        if (start > 0) {
            int space = line.indexOf(' ', start);
            start = space >= 0 && space < at ? space + 1 : start;
        }
        if (end < line.length()) {
            int space = line.lastIndexOf(' ', end);
            end = space > at ? space : end;
        }
        return (start > 0 ? "…" : "") + line.substring(start, end) + (end < line.length() ? "…" : "");
    }

    private static String clientName(Client client) {
        return client == null ? null : client.getCompanyName();
    }
}
