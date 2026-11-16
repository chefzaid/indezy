package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.enums.NetworkAvailability;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkFollowRequestRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The member directory (see docs/features.md#member-network): the members a member can see, found by a skill, a city,
 * an availability or remote work, on what their cards show only, so a hidden field is never searched.
 */
@Service
@RequiredArgsConstructor
public class NetworkDirectoryService {

    static final int PAGE_SIZE = 20;
    /** How many members a mention suggests. */
    static final int MENTIONED = 5;

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkFollowRepository follows;
    private final NetworkFollowRequestRepository requests;

    /** What the directory is asked for; every criterion is optional. */
    public record Search(String skill, String city, NetworkAvailability availability, Boolean remote, int page) {
    }

    /** A member found, with the card the viewer reads. */
    private record Found(NetworkMember member, NetworkDtos.MemberCard card) {

        String handle() {
            return card.handle();
        }
    }

    @Transactional(readOnly = true)
    public NetworkDtos.Directory search(Long viewerId, Search search) {
        network.requireMember(viewerId);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(viewerId));
        String skill = plain(search.skill());
        String city = plain(search.city());
        List<Found> found = members.findByActiveTrue().stream()
            .filter(member -> !member.getUserId().equals(viewerId) && !blocked.contains(member.getUserId()))
            .flatMap(member -> network.liveCard(member).map(card -> new Found(member, card)).stream())
            .filter(hit -> skill.isEmpty() || hit.card().stack().stream().anyMatch(name -> plain(name).equals(skill)))
            .filter(hit -> city.isEmpty() || (hit.card().city() != null && plain(hit.card().city()).contains(city)))
            .filter(hit -> search.availability() == null || hit.card().availability() == search.availability())
            .filter(hit -> search.remote() == null || hit.card().remote() == search.remote())
            .sorted(Comparator.comparing((Found hit) -> plain(hit.card().lastName())).thenComparing(hit -> plain(hit.card().firstName())))
            .toList();
        int page = Math.max(0, search.page());
        List<Found> shown = found.stream().skip((long) page * PAGE_SIZE).limit(PAGE_SIZE).toList();
        List<String> followed = shown.stream()
            .filter(hit -> follows.findByFollowerIdAndFollowedId(viewerId, hit.member().getUserId()).isPresent()).map(Found::handle).toList();
        List<String> requested = shown.stream()
            .filter(hit -> requests.findByRequesterIdAndMemberId(viewerId, hit.member().getUserId()).isPresent()).map(Found::handle).toList();
        List<String> privateCards = shown.stream().filter(hit -> hit.member().isPrivate()).map(Found::handle).toList();
        return new NetworkDtos.Directory(shown.stream().map(Found::card).toList(), found.size(), page, PAGE_SIZE, followed, requested,
            privateCards);
    }

    /**
     * The members to mention for what was typed after an @: those whose handle, first or last name starts with it, accents
     * and case aside, the members the viewer follows first, then by last name; five at most, never the viewer nor a member
     * either one blocks.
     */
    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> mentions(Long viewerId, String typed) {
        network.requireMember(viewerId);
        String start = plain(Optional.ofNullable(typed).orElse("").strip().replaceFirst("^@", ""));
        if (start.isEmpty()) {
            return List.of();
        }
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(viewerId));
        Set<Long> followed = new HashSet<>();
        follows.findByFollowerId(viewerId).forEach(follow -> followed.add(follow.getFollowedId()));
        return members.findByActiveTrue().stream()
            .filter(member -> !member.getUserId().equals(viewerId) && !blocked.contains(member.getUserId()))
            .flatMap(member -> network.liveCard(member).map(card -> new Found(member, card)).stream())
            .filter(hit -> plain(hit.handle()).startsWith(start) || plain(hit.card().firstName()).startsWith(start) ||
                plain(hit.card().lastName()).startsWith(start))
            .sorted(Comparator.comparing((Found hit) -> !followed.contains(hit.member().getUserId()))
                .thenComparing(hit -> plain(hit.card().lastName())).thenComparing(Found::handle))
            .limit(MENTIONED).map(Found::card).toList();
    }

    /** Lower case, accents and surrounding spaces dropped, to compare as people type. */
    private static String plain(String text) {
        return text == null ? "" : Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
