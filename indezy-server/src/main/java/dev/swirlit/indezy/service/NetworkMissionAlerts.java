package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tells the members whose card matches a mission passed on (see docs/features.md#member-network): a skill the mission
 * asks that their card shows, and, unless it is fully remote, their city when it names one; 200 members at most, never its
 * author, as each chose in Profile → Network.
 */
@Component
@RequiredArgsConstructor
public class NetworkMissionAlerts {

    /** How many members a mission tells at most. */
    static final int TOLD_MAX = 200;
    /** Days a week remote that make a mission's city no matter. */
    static final int FULLY_REMOTE = 5;

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkNotificationService notices;

    /** A mission was just passed on. */
    void published(NetworkPost post) {
        NetworkDtos.PostDetails details = NetworkPostKinds.toDto(post.getDetails());
        if (details == null || details.stack().isEmpty()) {
            return;
        }
        Set<String> asked = details.stack().stream().map(NetworkPost::fold).collect(Collectors.toSet());
        String city = details.remoteDays() != null && details.remoteDays() >= FULLY_REMOTE ? null : NetworkPost.fold(details.city());
        List<NetworkMember> told = members.findByActiveTrue().stream()
            .filter(member -> !member.getUserId().equals(post.getAuthorId()))
            .filter(member -> network.liveCard(member).filter(card -> matches(card, asked, city)).isPresent())
            .limit(TOLD_MAX).toList();
        told.forEach(member -> notices.notify(member.getUserId(), post.getAuthorId(), NetworkNotificationType.MISSION_MATCH, post.getId()));
    }

    private static boolean matches(NetworkDtos.MemberCard card, Set<String> asked, String city) {
        boolean skill = card.stack().stream().map(NetworkPost::fold).anyMatch(asked::contains);
        boolean place = city == null || city.isEmpty() || card.remote() || card.city() != null && NetworkPost.fold(card.city()).contains(city);
        return skill && place;
    }
}
