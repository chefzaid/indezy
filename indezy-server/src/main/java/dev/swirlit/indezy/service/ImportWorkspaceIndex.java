package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SeasonRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** What a workspace already holds, by the keys an import matches records on. */
@Component
@RequiredArgsConstructor
class ImportWorkspaceIndex {

    private final SourceRepository sourceRepository;
    private final ClientRepository clientRepository;
    private final ContactRepository contactRepository;
    private final SeasonRepository seasonRepository;
    private final ProjectRepository projectRepository;

    /** Ids of the workspace's records by key; an import adds the records it creates or would create. */
    record Keys(Map<String, Long> sources, Map<String, Long> clients, Map<String, Long> contacts, Map<String, Long> seasons,
                Map<String, Long> projects) {
    }

    Keys of(Long freelanceId) {
        Keys keys = new Keys(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        sourceRepository.findByFreelanceId(freelanceId).forEach(source -> keys.sources().putIfAbsent(name(source.getName()), source.getId()));
        clientRepository.findByFreelanceId(freelanceId)
            .forEach(client -> keys.clients().putIfAbsent(name(client.getCompanyName()), client.getId()));
        for (Contact contact : contactRepository.findByFreelanceId(freelanceId)) {
            String company = contact.getClient() == null ? null : contact.getClient().getCompanyName();
            keys.contacts().putIfAbsent(contact(contact.getEmail(), contact.getFirstName(), contact.getLastName(), company),
                contact.getId());
        }
        seasonRepository.findByFreelanceIdOrderByStartDateDesc(freelanceId)
            .forEach(season -> keys.seasons().putIfAbsent(name(season.getName()), season.getId()));
        for (Project project : projectRepository.findByFreelanceId(freelanceId)) {
            String client = project.getClient() == null ? null : project.getClient().getCompanyName();
            keys.projects().putIfAbsent(project(project.getRole(), client, project.getStartDate()), project.getId());
        }
        return keys;
    }

    /** A name folded for matching: case, accents and punctuation aside. */
    static String name(String name) {
        return Objects.requireNonNullElse(TextFolding.words(name), "");
    }

    /** A contact by email when it has one, else by name at its company. */
    static String contact(String email, String firstName, String lastName, String company) {
        if (email != null && !email.isBlank()) {
            return "email:" + email.strip().toLowerCase(java.util.Locale.ROOT);
        }
        return "name:" + name(Objects.requireNonNullElse(firstName, "") + " " + Objects.requireNonNullElse(lastName, "")) + "|" +
            name(company);
    }

    static String project(String role, String client, LocalDate start) {
        return name(role) + "|" + name(client) + "|" + start;
    }
}
