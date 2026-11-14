package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.enums.NetworkAvailability;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
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

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkRestrictionRepository restrictions;

    /** What the directory is asked for; every criterion is optional. */
    public record Search(String skill, String city, NetworkAvailability availability, Boolean remote, int page) {
    }

    @Transactional(readOnly = true)
    public NetworkDtos.Directory search(Long viewerId, Search search) {
        network.requireMember(viewerId);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(viewerId));
        String skill = plain(search.skill());
        String city = plain(search.city());
        List<NetworkDtos.MemberCard> found = members.findByActiveTrue().stream()
            .filter(member -> !member.getUserId().equals(viewerId) && !blocked.contains(member.getUserId()))
            .map(network::liveCard).flatMap(Optional::stream)
            .filter(card -> skill.isEmpty() || card.stack().stream().anyMatch(name -> plain(name).equals(skill)))
            .filter(card -> city.isEmpty() || (card.city() != null && plain(card.city()).contains(city)))
            .filter(card -> search.availability() == null || card.availability() == search.availability())
            .filter(card -> search.remote() == null || card.remote() == search.remote())
            .sorted(Comparator.comparing((NetworkDtos.MemberCard card) -> plain(card.lastName())).thenComparing(card -> plain(card.firstName())))
            .toList();
        int page = Math.max(0, search.page());
        List<NetworkDtos.MemberCard> shown = found.stream().skip((long) page * PAGE_SIZE).limit(PAGE_SIZE).toList();
        return new NetworkDtos.Directory(shown, found.size(), page, PAGE_SIZE);
    }

    /** Lower case, accents and surrounding spaces dropped, to compare as people type. */
    private static String plain(String text) {
        return text == null ? "" : Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
