package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A member's **About** section and past missions (see docs/features.md#member-network): a few words they write, and, when
 * they choose, the missions they won (role, stack, period; never the client), shown on their page like their posts.
 */
@Service
@RequiredArgsConstructor
public class NetworkAboutService {

    /** How many past missions a page lists, the latest first. */
    static final int MISSIONS_SHOWN = 10;

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final NetworkMemberRepository members;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ProjectRepository projects;

    /** What the member wrote about themselves, and whether their missions show. */
    @Transactional(readOnly = true)
    public NetworkDtos.About mine(Long userId) {
        NetworkMember member = network.requireMember(userId);
        return new NetworkDtos.About(member.getAbout(), Boolean.TRUE.equals(member.getShowMissions()));
    }

    /** Saves the About section (blank clears it) and whether the won missions show. */
    @Transactional
    public NetworkDtos.About save(Long userId, NetworkDtos.About about) {
        NetworkMember member = network.requireMember(userId);
        String text = about.about() == null ? null : about.about().strip();
        member.setAbout(text == null || text.isEmpty() ? null : text);
        member.setShowMissions(about.showMissions());
        members.save(member);
        return mine(userId);
    }

    /** The member's About section and listed missions; nothing of a private card to a member not following it. */
    @Transactional(readOnly = true)
    public NetworkDtos.MemberAbout of(Long viewerId, String handle) {
        NetworkMember member = follows.visible(network.requireMember(viewerId), handle);
        if (follows.locked(viewerId).contains(member.getUserId())) {
            return new NetworkDtos.MemberAbout(null, List.of());
        }
        List<NetworkDtos.PastMission> missions = Boolean.TRUE.equals(member.getShowMissions()) ? missions(member.getUserId()) : List.of();
        return new NetworkDtos.MemberAbout(member.getAbout(), missions);
    }

    private List<NetworkDtos.PastMission> missions(Long userId) {
        return users.findById(userId).flatMap(user -> freelances.findByEmail(user.getEmail()))
            .map(freelance -> projects.findByFreelanceId(freelance.getId())).orElse(List.of()).stream()
            .filter(project -> project.getStatus() == ProjectStatus.WON && project.getStartDate() != null)
            .sorted(Comparator.comparing(Project::getStartDate).reversed())
            .limit(MISSIONS_SHOWN)
            .map(project -> new NetworkDtos.PastMission(project.getRole(), stack(project.getTechStack()), project.getStartDate(), project.lastDay()))
            .toList();
    }

    private static List<String> stack(String techStack) {
        return techStack == null ? List.of() : Arrays.stream(techStack.split("[,;]")).map(String::strip).filter(Objects::nonNull)
            .filter(skill -> !skill.isEmpty()).distinct().limit(8).toList();
    }
}
