package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.util.TextFolding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stages to update, read from what a contact recently said about an opportunity (see
 * docs/features.md#dashboard-and-analytics): an exchange from them, recorded on an open opportunity within
 * {@value #RECENT_DAYS} days and not before the opportunity last changed, that turns it down suggests Abandoned; one
 * that asks for an interview while it is at first contact suggests Interview. Words only, in French and English, no AI.
 */
final class StatusSuggestions {

    static final int RECENT_DAYS = 21;
    private static final int EXCERPT_MAX = 140;
    private static final Set<ProjectStatus> OPEN = EnumSet.of(ProjectStatus.CONTACT, ProjectStatus.INTERVIEW, ProjectStatus.OFFER);
    private static final Pattern REFUSAL = Pattern.compile("\\b(pas|non)( ete)? retenue?s?\\b|(ne|pas) (pas )?(y )?donner(ons)? (pas )?suite|" +
        "sans suite|pas de suite|refus|declin|autre (candidat|profil)|other candidate|not (been )?selected|not (to )?(move|moving) forward|" +
        "unfortunately|malheureusement|(poste|mission|position) (est |a ete |has been |was )?(pourvue?|filled)|regret");
    private static final Pattern INVITE = Pattern.compile("\\b(entretien|interview|rencontrer|visio|rendez vous|rdv|call|" +
        "disponibilites|creneaux?|slot)s?\\b");

    private StatusSuggestions() {
    }

    /** One suggestion per opportunity at most, from its latest telling exchange; the latest first. */
    static List<DashboardStatsDto.StatusSuggestion> build(List<ContactInteraction> all, LocalDate today) {
        LocalDate since = today.minusDays(RECENT_DAYS);
        List<ContactInteraction> recent = all.stream()
            .filter(interaction -> interaction.getDirection() == InteractionDirection.INCOMING && interaction.getProject() != null &&
                interaction.getSummary() != null && interaction.getOccurredOn() != null && !interaction.getOccurredOn().isBefore(since))
            .sorted(Comparator.comparing(ContactInteraction::getOccurredOn).reversed()).toList();
        Set<Long> seen = new HashSet<>();
        List<DashboardStatsDto.StatusSuggestion> suggestions = new ArrayList<>();
        for (ContactInteraction interaction : recent) {
            Project project = interaction.getProject();
            if (!OPEN.contains(project.getStatus()) || project.getArchivedAt() != null || seen.contains(project.getId()) ||
                project.getUpdatedAt() != null && interaction.getOccurredOn().isBefore(project.getUpdatedAt().toLocalDate())) {
                continue;
            }
            ProjectStatus suggested = suggest(interaction.getSummary(), project.getStatus());
            if (suggested == null) {
                continue;
            }
            seen.add(project.getId());
            String summary = interaction.getSummary().strip();
            suggestions.add(DashboardStatsDto.StatusSuggestion.builder()
                .projectId(project.getId())
                .interactionId(interaction.getId())
                .role(project.getRole())
                .clientName(project.getClient() == null ? null : project.getClient().getCompanyName())
                .status(project.getStatus())
                .suggested(suggested)
                .excerpt(summary.length() > EXCERPT_MAX ? summary.substring(0, EXCERPT_MAX).strip() + "…" : summary)
                .occurredOn(interaction.getOccurredOn())
                .build());
        }
        return suggestions;
    }

    /** The stage the words suggest for an opportunity at {@code current}; null when they suggest none. */
    static ProjectStatus suggest(String text, ProjectStatus current) {
        String folded = Objects.toString(TextFolding.words(text), "").replace(".", " ");
        if (REFUSAL.matcher(folded).find()) {
            return ProjectStatus.LOST;
        }
        return current == ProjectStatus.CONTACT && INVITE.matcher(folded).find() ? ProjectStatus.INTERVIEW : null;
    }
}
