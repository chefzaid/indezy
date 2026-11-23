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
 * or the ESN), and the missions among them won.
 */
@Service
@RequiredArgsConstructor
public class ProspectionCampaignService {

    private final ProspectionCampaignRepository campaigns;
    private final ContactInteractionRepository interactions;
    private final ScheduledContactEmailRepository scheduled;
    private final ProjectRepository projects;

    /** A campaign and its results. */
    public record Campaign(Long id, String name, LocalDateTime startedAt, int sent, int waiting, int answered, int opportunities, int won) {
    }

    @Transactional
    public Campaign start(Long freelanceId, String name) {
        ProspectionCampaign campaign = new ProspectionCampaign();
        campaign.setFreelanceId(freelanceId);
        String stripped = name == null || name.isBlank() ? "Prospection" : name.strip();
        campaign.setName(stripped.length() > ProspectionCampaign.NAME_MAX ? stripped.substring(0, ProspectionCampaign.NAME_MAX) : stripped);
        ProspectionCampaign saved = campaigns.save(campaign);
        return new Campaign(saved.getId(), saved.getName(), saved.getCreatedAt(), 0, 0, 0, 0, 0);
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
        Map<Long, Integer> waiting = new HashMap<>();
        scheduled.findByCampaignIdIn(ids).stream().map(ScheduledContactEmail::getCampaignId).forEach(id -> waiting.merge(id, 1, Integer::sum));
        List<Project> pipeline = projects.findByFreelanceId(freelanceId);
        Map<Long, List<ContactInteraction>> exchanges = new HashMap<>();
        sent.values().stream().flatMap(List::stream).map(email -> email.getContact().getId()).distinct()
            .forEach(contactId -> exchanges.put(contactId, interactions.findByContactIdOrderByOccurredOnDescIdDesc(contactId)));
        return latest.stream().map(campaign -> results(campaign, sent.getOrDefault(campaign.getId(), List.of()),
                waiting.getOrDefault(campaign.getId(), 0), exchanges, pipeline))
            .filter(campaign -> campaign.sent() + campaign.waiting() > 0).toList();
    }

    private static Campaign results(ProspectionCampaign campaign, List<ContactInteraction> sent, int waiting,
                                    Map<Long, List<ContactInteraction>> exchanges, List<Project> pipeline) {
        Set<Long> answered = new HashSet<>();
        Set<Long> companies = new HashSet<>();
        for (ContactInteraction email : sent) {
            Long contactId = email.getContact().getId();
            if (email.getContact().getClient() != null) {
                companies.add(email.getContact().getClient().getId());
            }
            LocalDate sentOn = email.getOccurredOn();
            boolean replied = Boolean.TRUE.equals(email.getReplied()) || exchanges.getOrDefault(contactId, List.of()).stream()
                .anyMatch(other -> other.getDirection() == InteractionDirection.INCOMING && !other.getOccurredOn().isBefore(sentOn));
            if (replied) {
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
        return new Campaign(campaign.getId(), campaign.getName(), started, sent.size(), waiting, answered.size(), opened.size(), won);
    }
}
