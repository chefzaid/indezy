package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.ProspectionCampaign;
import dev.swirlit.indezy.model.ScheduledContactEmail;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.ProspectionCampaignRepository;
import dev.swirlit.indezy.repository.ScheduledContactEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The workspace's prospection campaigns and what came of each (see docs/features.md#emails-sent-from-indezy): the emails
 * sent and waiting, the contacts who answered since, the opportunities opened since with their companies (as the client
 * or the ESN), their interview steps (the calls booked), and the missions among them won.
 */
@Service
@RequiredArgsConstructor
public class ProspectionCampaignService {

    private final ProspectionCampaignRepository campaigns;
    private final ContactInteractionRepository interactions;
    private final ScheduledContactEmailRepository scheduled;
    private final ProjectRepository projects;

    /** A campaign and its results, with the contacts it wrote or plans to write to (for **Prospect again**). */
    public record Campaign(Long id, String name, LocalDateTime startedAt, int sent, int waiting, int answered, int opportunities, int interviews,
                           int won, List<Long> contactIds) {

        public Campaign {
            contactIds = contactIds == null ? List.of() : List.copyOf(contactIds);
        }
    }

    @Transactional
    public Campaign start(Long freelanceId, String name) {
        ProspectionCampaign campaign = new ProspectionCampaign();
        campaign.setFreelanceId(freelanceId);
        String stripped = name == null || name.isBlank() ? "Prospection" : name.strip();
        campaign.setName(stripped.length() > ProspectionCampaign.NAME_MAX ? stripped.substring(0, ProspectionCampaign.NAME_MAX) : stripped);
        ProspectionCampaign saved = campaigns.save(campaign);
        return new Campaign(saved.getId(), saved.getName(), saved.getCreatedAt(), 0, 0, 0, 0, 0, 0, List.of());
    }

    /** The campaign, refused when it is another workspace's. */
    @Transactional(readOnly = true)
    public Long require(Long freelanceId, Long id) {
        return campaigns.findByIdAndFreelanceId(id, freelanceId).map(ProspectionCampaign::getId)
            .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id: " + id));
    }

    /** The latest twenty campaigns with their results, the newest first; those that sent nothing and wait for nothing left out. */
    @Transactional(readOnly = true)
    public List<Campaign> list(Long freelanceId) {
        List<ProspectionCampaign> latest = campaigns.findTop20ByFreelanceIdOrderByCreatedAtDescIdDesc(freelanceId);
        if (latest.isEmpty()) {
            return List.of();
        }
        List<Long> ids = latest.stream().map(ProspectionCampaign::getId).toList();
        Map<Long, List<ContactInteraction>> sent = new HashMap<>();
        interactions.findByCampaignIdIn(ids).forEach(email -> sent.computeIfAbsent(email.getCampaignId(), key -> new java.util.ArrayList<>()).add(email));
        Map<Long, List<ScheduledContactEmail>> waiting = new HashMap<>();
        // A reminder is not one of the campaign's emails still to go: it goes only to those who did not answer.
        scheduled.findByCampaignIdIn(ids).stream().filter(email -> !Boolean.TRUE.equals(email.getReminder()))
            .forEach(email -> waiting.computeIfAbsent(email.getCampaignId(), key -> new java.util.ArrayList<>()).add(email));
        List<Project> pipeline = projects.findByFreelanceId(freelanceId);
        Map<Long, List<ContactInteraction>> exchanges = new HashMap<>();
        sent.values().stream().flatMap(List::stream).map(email -> email.getContact().getId()).distinct()
            .forEach(contactId -> exchanges.put(contactId, interactions.findByContactIdOrderByOccurredOnDescIdDesc(contactId)));
        return latest.stream().map(campaign -> results(campaign, sent.getOrDefault(campaign.getId(), List.of()),
                waiting.getOrDefault(campaign.getId(), List.of()), exchanges, pipeline))
            .filter(campaign -> campaign.sent() + campaign.waiting() > 0).toList();
    }

    /** Whether the contact answered this email: it is marked replied, or they wrote on or after the day it went. */
    static boolean answered(ContactInteraction email, List<ContactInteraction> exchanges) {
        LocalDate sentOn = email.getOccurredOn();
        return Boolean.TRUE.equals(email.getReplied()) || exchanges.stream()
            .anyMatch(other -> other.getDirection() == InteractionDirection.INCOMING && !other.getOccurredOn().isBefore(sentOn));
    }

    private static Campaign results(ProspectionCampaign campaign, List<ContactInteraction> sent, List<ScheduledContactEmail> waiting,
                                    Map<Long, List<ContactInteraction>> exchanges, List<Project> pipeline) {
        Set<Long> answered = new HashSet<>();
        Set<Long> companies = new HashSet<>();
        for (ContactInteraction email : sent) {
            Long contactId = email.getContact().getId();
            if (email.getContact().getClient() != null) {
                companies.add(email.getContact().getClient().getId());
            }
            if (answered(email, exchanges.getOrDefault(contactId, List.of()))) {
                answered.add(contactId);
            }
        }
        LocalDateTime started = campaign.getCreatedAt();
        List<Project> opened = pipeline.stream().filter(project -> project.getCreatedAt() != null && started != null &&
                !project.getCreatedAt().isBefore(started))
            .filter(project -> Stream.of(project.getClient(), project.getMiddleman()).filter(Objects::nonNull).map(Client::getId)
                .anyMatch(companies::contains))
            .toList();
        int won = (int) opened.stream().filter(project -> project.getStatus() == ProjectStatus.WON).count();
        // The calls booked: the interview steps of those opportunities.
        int interviews = opened.stream().mapToInt(project -> project.getSteps().size()).sum();
        List<Long> contacts = Stream.concat(sent.stream().map(email -> email.getContact().getId()),
            waiting.stream().map(email -> email.getContact().getId())).distinct().toList();
        return new Campaign(campaign.getId(), campaign.getName(), started, sent.size(), waiting.size(), answered.size(), opened.size(), interviews,
            won, contacts);
    }
}
