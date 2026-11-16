package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkMissionTake;
import dev.swirlit.indezy.model.NetworkOpportunityCard;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkPostDetails;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.SourceType;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkMissionTakeRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * **Add to my pipeline** on a mission passed on in the network (see docs/features.md#member-network): the reader's
 * opportunity at Contact, filled from the mission's facts, its client the one the author named or else a confidential
 * client, its source the author on the network; the author sees how many members took it, and who among those who agree.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkPipelineService {

    static final String CONFIDENTIAL_CLIENT = "Client confidentiel";
    private static final double WEEKS_PER_MONTH = 52.0 / 12;

    private final NetworkPostService posts;
    private final NetworkService network;
    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final NetworkMissionTakeRepository takes;
    private final NetworkMemberRepository members;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ClientRepository clients;
    private final SourceRepository sources;

    /** Adds the mission to the reader's pipeline, once; answers the opportunity it became. */
    @Transactional
    public ProjectDto take(Long userId, Long postId, boolean named) {
        NetworkPost post = posts.readable(userId, postId);
        if (post.getKind() != NetworkPostKind.MISSION || post.getDetails() == null) {
            throw new InvalidStateException("Only a mission passed on goes to a pipeline");
        }
        if (post.getAuthorId().equals(userId)) {
            throw new InvalidStateException("This mission is yours");
        }
        Optional<NetworkMissionTake> earlier = takes.findByPostIdAndUserId(postId, userId);
        if (earlier.isPresent() && projectRepository.existsById(earlier.get().getProjectId())) {
            throw new InvalidStateException("This mission is already in your pipeline");
        }
        Freelance freelance = workspace(userId);
        String handle = members.findByUserId(post.getAuthorId()).map(NetworkMember::getHandle).orElse("member");
        NetworkPostDetails facts = post.getDetails();
        ProjectDto project = new ProjectDto();
        project.setRole(facts.getRole());
        project.setStatus(ProjectStatus.CONTACT);
        project.setDescription(post.getText() + "\n\n— @" + handle);
        project.setTechStack(facts.getStack() == null ? null : String.join(", ", facts.getStack().split(",")));
        project.setDailyRate(facts.getRateMax() != null ? facts.getRateMax() : facts.getRateMin() != null ? facts.getRateMin() :
            freelances.findTargetDailyRate(freelance.getId()).orElse(0));
        project.setRemoteDaysPerMonth(facts.getRemoteDays() == null ? null : (int) Math.round(facts.getRemoteDays() * WEEKS_PER_MONTH));
        project.setStartDate(facts.getStart());
        project.setWorkAddress(facts.getCity());
        project.setFreelanceId(freelance.getId());
        project.setClientId(client(freelance, facts.getClient() != null ? facts.getClient() : CONFIDENTIAL_CLIENT, facts.getCity()).getId());
        project.setSourceId(source(freelance, handle).getId());
        ProjectDto created = projects.create(project);
        NetworkMissionTake take = earlier.orElseGet(NetworkMissionTake::new);
        take.setPostId(postId);
        take.setUserId(userId);
        take.setProjectId(created.getId());
        take.setNamed(named);
        takes.save(take);
        log.info("Member {} took mission {} into project {}", userId, postId, created.getId());
        return created;
    }

    /** The reader's own opportunity, as a card to share in a message: its facts, never its client. */
    @Transactional(readOnly = true)
    public NetworkOpportunityCard card(Long userId, Long projectId) {
        Freelance freelance = workspace(userId);
        Project project = projectRepository.findById(projectId).filter(found -> found.getFreelance() != null &&
            found.getFreelance().getId().equals(freelance.getId())).orElseThrow(() -> new ResourceNotFoundException("No such opportunity"));
        NetworkOpportunityCard card = new NetworkOpportunityCard();
        card.setRole(project.getRole());
        card.setDailyRate(project.getDailyRate());
        card.setCity(project.getWorkAddress() != null ? project.getWorkAddress() : project.getClient() == null ? null : project.getClient().getCity());
        card.setStartDate(project.getStartDate());
        card.setRemoteDaysPerMonth(project.getRemoteDaysPerMonth());
        card.setTechStack(project.getTechStack());
        return card;
    }

    /**
     * Adds an opportunity shared in a message to the reader's pipeline, once: at Contact, its client a confidential one,
     * its source the sender on the network, as the one who referred it.
     */
    @Transactional
    public ProjectDto takeCard(Long userId, NetworkOpportunityCard card, Long senderId) {
        if (card.getTakenProjectId() != null && projectRepository.existsById(card.getTakenProjectId())) {
            throw new InvalidStateException("This opportunity is already in your pipeline");
        }
        Freelance freelance = workspace(userId);
        String handle = members.findByUserId(senderId).map(NetworkMember::getHandle).orElse("member");
        ProjectDto project = new ProjectDto();
        project.setRole(card.getRole());
        project.setStatus(ProjectStatus.CONTACT);
        project.setDescription("— @" + handle);
        project.setTechStack(card.getTechStack());
        project.setDailyRate(card.getDailyRate() != null ? card.getDailyRate() : freelances.findTargetDailyRate(freelance.getId()).orElse(0));
        project.setRemoteDaysPerMonth(card.getRemoteDaysPerMonth());
        project.setStartDate(card.getStartDate());
        project.setWorkAddress(card.getCity());
        project.setFreelanceId(freelance.getId());
        project.setClientId(client(freelance, CONFIDENTIAL_CLIENT, card.getCity()).getId());
        project.setSourceId(source(freelance, handle).getId());
        ProjectDto created = projects.create(project);
        card.setTakenProjectId(created.getId());
        log.info("Member {} took a shared opportunity into project {}", userId, created.getId());
        return created;
    }

    /** The members who took the reader's own mission and let them know, the earliest first. */
    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> takers(Long userId, Long postId) {
        NetworkPost post = posts.readable(userId, postId);
        if (!post.getAuthorId().equals(userId)) {
            throw new ResourceNotFoundException(NetworkPostService.NOT_FOUND);
        }
        return takes.findByPostIdAndNamedTrueOrderByCreatedAtAsc(postId).stream()
            .map(take -> members.findByUserId(take.getUserId()).filter(NetworkMember::isActive).flatMap(network::liveCard))
            .flatMap(Optional::stream).toList();
    }

    private Freelance workspace(Long userId) {
        return users.findById(userId).flatMap(user -> freelances.findByEmail(user.getEmail()))
            .orElseThrow(() -> new InvalidStateException("Your workspace is not set up yet"));
    }

    /** The workspace's client of this name, created when missing in the city given (France when none). */
    private Client client(Freelance freelance, String name, String city) {
        return clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(freelance.getId(), name).orElseGet(() -> {
            Client client = new Client();
            client.setCompanyName(name);
            client.setCity(city != null ? city : "France");
            client.setIsFinal(true);
            client.setFreelance(freelance);
            return clients.save(client);
        });
    }

    /** The author on the network as the opportunity's source, created when missing. */
    private Source source(Freelance freelance, String handle) {
        String name = "Indezy @" + handle;
        return sources.findFirstByFreelanceIdAndNameIgnoreCase(freelance.getId(), name).orElseGet(() -> {
            Source source = new Source();
            source.setName(name);
            source.setType(SourceType.SOCIAL_MEDIA);
            source.setIsListing(false);
            source.setFreelance(freelance);
            return sources.save(source);
        });
    }
}
