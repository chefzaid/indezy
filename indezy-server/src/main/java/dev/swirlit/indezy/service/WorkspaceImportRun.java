package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ClientDto;
import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.dto.ProjectNoteDto;
import dev.swirlit.indezy.dto.SeasonDto;
import dev.swirlit.indezy.dto.SourceDto;
import dev.swirlit.indezy.dto.WorkspaceImportFile;
import dev.swirlit.indezy.dto.WorkspaceImportReport;
import dev.swirlit.indezy.dto.WorkspaceImportReport.Kind;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One import of a file into a workspace, in dependency order: sources and clients, then contacts and seasons, then
 * opportunities and their steps, then journal notes. Ids of the file are mapped to the workspace's as records are
 * matched or created; a preview (no writers) maps a record it would create to {@link #PENDING}.
 */
final class WorkspaceImportRun {

    /** Id given in a preview to a record the import would create. */
    static final long PENDING = -1L;

    private final Long freelanceId;
    private final ImportWorkspaceIndex.Keys known;
    private final Validator validator;
    private final ImportWriters writers;

    private final Map<Long, Long> sourceIds = new HashMap<>();
    private final Map<Long, Long> clientIds = new HashMap<>();
    private final Map<Long, String> clientNames = new HashMap<>();
    private final Map<Long, Long> seasonIds = new HashMap<>();
    private final Map<Long, Long> createdProjects = new HashMap<>();
    private final Set<Long> matchedProjects = new HashSet<>();

    WorkspaceImportRun(Long freelanceId, ImportWorkspaceIndex.Keys known, Validator validator, ImportWriters writers) {
        this.freelanceId = freelanceId;
        this.known = known;
        this.validator = validator;
        this.writers = writers;
    }

    WorkspaceImportReport run(WorkspaceImportFile file) {
        return new WorkspaceImportReport(writers != null, List.of(sources(file.sources()), clients(file.clients()),
            contacts(file.contacts()), seasons(file.seasons()), projects(file.projects()), notes(file.projectNotes())));
    }

    private WorkspaceImportReport.Section sources(List<SourceDto> items) {
        ImportSectionBuilder section = new ImportSectionBuilder(Kind.SOURCES);
        for (SourceDto source : items) {
            Long fileId = source.getId();
            source.setId(null);
            source.setFreelanceId(freelanceId);
            String key = ImportWorkspaceIndex.name(source.getName());
            map(fileId, sourceIds, match(section, source.getName(), key, known.sources(), source,
                dto -> writers.sources().create(dto).getId()));
        }
        return section.build();
    }

    private WorkspaceImportReport.Section clients(List<ClientDto> items) {
        ImportSectionBuilder section = new ImportSectionBuilder(Kind.CLIENTS);
        for (ClientDto client : items) {
            Long fileId = client.getId();
            client.setId(null);
            client.setFreelanceId(freelanceId);
            if (fileId != null) {
                clientNames.put(fileId, client.getCompanyName());
            }
            String key = ImportWorkspaceIndex.name(client.getCompanyName());
            map(fileId, clientIds, match(section, client.getCompanyName(), key, known.clients(), client,
                dto -> writers.clients().create(dto).getId()));
        }
        return section.build();
    }

    private WorkspaceImportReport.Section contacts(List<ContactDto> items) {
        ImportSectionBuilder section = new ImportSectionBuilder(Kind.CONTACTS);
        for (ContactDto contact : items) {
            String label = (nullToEmpty(contact.getFirstName()) + " " + nullToEmpty(contact.getLastName())).strip();
            Long client = clientIds.get(contact.getClientId());
            if (client == null) {
                section.invalid(label, "Its company is not in the file");
                continue;
            }
            String company = clientNames.getOrDefault(contact.getClientId(), contact.getClientName());
            contact.setId(null);
            contact.setFreelanceId(freelanceId);
            contact.setClientId(client);
            // Managers are left for the contact's page: the one in the file may not be imported.
            contact.setManagerId(null);
            String key = ImportWorkspaceIndex.contact(contact.getEmail(), contact.getFirstName(), contact.getLastName(), company);
            match(section, label, key, known.contacts(), contact, dto -> writers.contacts().create(dto).getId());
        }
        return section.build();
    }

    private WorkspaceImportReport.Section seasons(List<SeasonDto> items) {
        ImportSectionBuilder section = new ImportSectionBuilder(Kind.SEASONS);
        for (SeasonDto season : items) {
            Long fileId = season.getId();
            season.setId(null);
            season.setFreelanceId(freelanceId);
            if (season.getStartDate() != null && season.getEndDate() != null && season.getEndDate().isBefore(season.getStartDate())) {
                section.invalid(season.getName(), "It ends before it starts");
                continue;
            }
            map(fileId, seasonIds, match(section, season.getName(), ImportWorkspaceIndex.name(season.getName()), known.seasons(),
                season, dto -> writers.seasons().create(dto).getId()));
        }
        return section.build();
    }

    private WorkspaceImportReport.Section projects(List<ProjectDto> items) {
        ImportSectionBuilder section = new ImportSectionBuilder(Kind.PROJECTS);
        for (ProjectDto project : items) {
            Long fileId = project.getId();
            Long client = clientIds.get(project.getClientId());
            if (client == null) {
                section.invalid(project.getRole(), "Its client is not in the file");
                continue;
            }
            String clientName = clientNames.getOrDefault(project.getClientId(), project.getClientName());
            // A file can say "steps": null, or hold null steps.
            List<InterviewStepDto> projectSteps = java.util.Objects.requireNonNullElse(project.getSteps(), List.<InterviewStepDto>of())
                .stream().filter(java.util.Objects::nonNull).toList();
            project.setId(null);
            project.setFreelanceId(freelanceId);
            project.setClientId(client);
            project.setMiddlemanId(clientIds.get(project.getMiddlemanId()));
            project.setSourceId(sourceIds.get(project.getSourceId()));
            project.setSeasonId(seasonIds.get(project.getSeasonId()));
            project.setSteps(null);
            String invalidStep = projectSteps.stream().map(this::violations).filter(reason -> !reason.isEmpty()).findFirst().orElse(null);
            if (invalidStep != null) {
                section.invalid(project.getRole(), "A step: " + invalidStep);
                continue;
            }
            String key = ImportWorkspaceIndex.project(project.getRole(), clientName, project.getStartDate());
            Matched matched = match(section, project.getRole(), key, known.projects(), project, dto -> {
                Long id = writers.projects().create(dto).getId();
                projectSteps.stream().sorted(Comparator.comparing(InterviewStepDto::getDate,
                    Comparator.nullsLast(Comparator.naturalOrder()))).forEach(step -> {
                        step.setId(null);
                        step.setProjectId(id);
                        writers.steps().create(step);
                    });
                return id;
            });
            if (fileId != null && matched != null) {
                if (matched.created()) {
                    createdProjects.put(fileId, matched.id());
                } else {
                    matchedProjects.add(fileId);
                }
            }
        }
        return section.build();
    }

    private WorkspaceImportReport.Section notes(List<ProjectNoteDto> items) {
        ImportSectionBuilder section = new ImportSectionBuilder(Kind.PROJECT_NOTES);
        for (ProjectNoteDto note : items) {
            String label = excerpt(note.getContent());
            if (note.getContent() == null || note.getContent().isBlank()) {
                section.invalid(label, "It is empty");
            } else if (matchedProjects.contains(note.getProjectId())) {
                // Its opportunity was already in the workspace, with its journal.
                section.duplicate(label);
            } else if (!createdProjects.containsKey(note.getProjectId())) {
                section.invalid(label, "Its opportunity is not imported");
            } else {
                if (writers != null) {
                    writers.notes().addNote(createdProjects.get(note.getProjectId()), note.getContent().strip());
                }
                section.add(label);
            }
        }
        return section.build();
    }

    /** The workspace id a record of the file stands for, and whether the import creates it. */
    private record Matched(long id, boolean created) {
    }

    /**
     * Records the item as a duplicate of a known record, invalid (null), or new: created when writing, {@link #PENDING}
     * in a preview.
     */
    private <T> Matched match(ImportSectionBuilder section, String label, String key, Map<String, Long> knownIds, T item,
                              Function<T, Long> create) {
        Long existing = knownIds.get(key);
        if (existing != null) {
            section.duplicate(label);
            return new Matched(existing, false);
        }
        String violations = violations(item);
        if (!violations.isEmpty()) {
            section.invalid(label, violations);
            return null;
        }
        long id = writers == null ? PENDING : create.apply(item);
        knownIds.put(key, id);
        section.add(label);
        return new Matched(id, true);
    }

    private static void map(Long fileId, Map<Long, Long> ids, Matched matched) {
        if (fileId != null && matched != null) {
            ids.put(fileId, matched.id());
        }
    }

    /** The rules the record breaks, as the API would answer them; empty when it keeps them all. */
    private <T> String violations(T item) {
        Set<ConstraintViolation<T>> found = validator.validate(item);
        return found.stream().map(WorkspaceImportRun::reason).sorted().collect(Collectors.joining(", "));
    }

    /** A rule's own message when it names the field (such as "City is required"), else the field and the message. */
    private static String reason(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString();
        String leaf = field.substring(field.lastIndexOf('.') + 1).replaceAll("\\[.*", "");
        return violation.getMessage().replace(" ", "").toLowerCase(java.util.Locale.ROOT).contains(leaf.toLowerCase(java.util.Locale.ROOT)) ?
            violation.getMessage() : field + " " + violation.getMessage();
    }

    private static String excerpt(String text) {
        String line = nullToEmpty(text).strip().replaceAll("\\s+", " ");
        return line.length() > 60 ? line.substring(0, 59) + "…" : line;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
