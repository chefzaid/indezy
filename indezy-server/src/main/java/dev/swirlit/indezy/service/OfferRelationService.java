package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferRelationDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The publishers of the feed the workspace has to do with (see docs/features.md#freelance-offer-feed), by their folded name: the
 * companies of the missions it won, as client or ESN, the end clients a running non-solicitation clause covers, and the
 * companies whose contacts never answered it.
 */
@Service
@RequiredArgsConstructor
public class OfferRelationService {

    private final ProjectRepository projects;
    private final ContactInteractionRepository interactions;
    private final ClientRepository clients;

    /** An end client's name must fold to this many letters at least to be looked for in an offer. */
    static final int END_CLIENT_MIN = 3;

    /**
     * The workspace's end clients, to spot in offers: the companies marked final, and the clients of its missions signed
     * through an ESN; the longest names first, each once.
     */
    @Transactional(readOnly = true)
    public List<String> endClients(Long freelanceId) {
        Map<String, String> byKey = new HashMap<>();
        clients.findByFreelanceId(freelanceId).stream().filter(client -> Boolean.TRUE.equals(client.getIsFinal()))
            .forEach(client -> putName(byKey, client.getCompanyName()));
        projects.findByFreelanceId(freelanceId).stream().filter(project -> project.getMiddleman() != null && project.getClient() != null)
            .forEach(project -> putName(byKey, project.getClient().getCompanyName()));
        return byKey.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed())
            .map(Map.Entry::getValue).toList();
    }

    /** The first of the end clients that the offer's title or description names, its publisher aside; none otherwise. */
    public static String guessEndClient(JobOffer offer, List<String> endClients) {
        if (endClients.isEmpty()) {
            return null;
        }
        // The folding keeps dots (".NET"): a sentence's last one is dropped so that "Airbus." names Airbus.
        String text = (" " + Objects.toString(TextFolding.words(Objects.toString(offer.getTitle(), "") + " " +
            Objects.toString(offer.getDescription(), "")), "") + " ").replaceAll("\\.+ ", " ");
        String publisher = offer.getCompanyKey() != null ? offer.getCompanyKey() : JobOffer.companyKeyOf(offer.getCompany());
        return endClients.stream().filter(name -> {
            String key = JobOffer.companyKeyOf(name);
            return key != null && !key.equals(publisher) && text.contains(" " + key + " ");
        }).findFirst().orElse(null);
    }

    private static void putName(Map<String, String> byKey, String name) {
        String key = JobOffer.companyKeyOf(name);
        if (key != null && key.replace(" ", "").length() >= END_CLIENT_MIN) {
            byKey.putIfAbsent(key, name.strip());
        }
    }

    /** The relations of workspace {@code freelanceId}'s publishers on {@code today}, by {@link JobOffer#companyKeyOf} name. */
    @Transactional(readOnly = true)
    public Map<String, OfferRelationDto> of(Long freelanceId, LocalDate today) {
        Map<String, OfferRelationDto> relations = of(projects.findByFreelanceId(freelanceId), today);
        withUnanswered(relations, ContactInteractionService.unansweredCompanies(
            interactions.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(freelanceId)));
        return relations;
    }

    /** Marks the publishers whose contacts never answered, beside what else relates them to the workspace. */
    static void withUnanswered(Map<String, OfferRelationDto> relations, Map<String, Integer> unanswered) {
        unanswered.forEach((name, count) -> {
            String key = JobOffer.companyKeyOf(name);
            if (key != null) {
                relations.merge(key, new OfferRelationDto(false, null, null, count), (known, silent) -> known.unanswered(count));
            }
        });
    }

    static Map<String, OfferRelationDto> of(List<Project> all, LocalDate today) {
        Map<String, OfferRelationDto> relations = new HashMap<>();
        for (Project mission : all) {
            if (mission.getStatus() != ProjectStatus.WON || mission.getArchivedAt() != null) {
                continue;
            }
            workedWith(relations, mission.getClient());
            workedWith(relations, mission.getMiddleman());
        }
        for (Project mission : all) {
            if (mission.getStatus() != ProjectStatus.WON || mission.getMiddleman() == null || mission.getClient() == null ||
                mission.getNonSolicitationMonths() == null) {
                continue;
            }
            LocalDate last = mission.lastDay();
            LocalDate until = last == null ? null : last.plusMonths(mission.getNonSolicitationMonths());
            String key = JobOffer.companyKeyOf(mission.getClient().getCompanyName());
            if (key == null || until != null && !until.isAfter(today)) {
                continue;
            }
            OfferRelationDto known = relations.get(key);
            // The clause running longest is the one to keep in mind; an open-ended mission runs longest.
            if (known == null || known.clauseEsn() == null || known.clauseUntil() != null && (until == null || until.isAfter(known.clauseUntil()))) {
                relations.put(key, new OfferRelationDto(known != null && known.workedWith(), mission.getMiddleman().getCompanyName(), until));
            }
        }
        return relations;
    }

    private static void workedWith(Map<String, OfferRelationDto> relations, Client company) {
        String key = company == null ? null : JobOffer.companyKeyOf(company.getCompanyName());
        if (key != null) {
            relations.putIfAbsent(key, new OfferRelationDto(true, null, null));
        }
    }
}
