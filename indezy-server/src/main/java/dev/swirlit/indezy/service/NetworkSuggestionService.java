package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Members to follow (see docs/features.md#member-network), each with its reason: one of the viewer's Indezy contacts,
 * followed by members the viewer follows, sharing skills with the viewer's card, or in the same city; on what the cards show only.
 */
@Service
@RequiredArgsConstructor
public class NetworkSuggestionService {

    static final int SHOWN = 10;

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkFollowRepository follows;
    private final NetworkRestrictionRepository restrictions;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ContactRepository contactRepository;

    private record Candidate(NetworkDtos.MemberCard card, boolean contact, int followedBy, String followedByName, List<String> sharedSkills,
                             boolean sameCity) {

        int score() {
            return (contact ? 10 : 0) + followedBy * 3 + sharedSkills.size() * 2 + (sameCity ? 1 : 0);
        }
    }

    @Transactional(readOnly = true)
    public List<NetworkDtos.Suggestion> suggestions(Long viewerId) {
        NetworkMember viewer = network.requireMember(viewerId);
        NetworkDtos.MemberCard own = network.card(viewer);
        Set<Long> excluded = new HashSet<>(restrictions.blockedEitherWay(viewerId));
        excluded.add(viewerId);
        List<NetworkMember> followed = follows.following(viewerId);
        followed.forEach(member -> excluded.add(member.getUserId()));
        // Who the members the viewer follows follow, and one of them to name.
        Map<Long, Integer> followedBy = new HashMap<>();
        Map<Long, String> namedBy = new HashMap<>();
        for (NetworkMember friend : followed) {
            Optional<NetworkDtos.MemberCard> friendCard = network.liveCard(friend);
            for (NetworkMember next : follows.following(friend.getUserId())) {
                followedBy.merge(next.getUserId(), 1, Integer::sum);
                friendCard.ifPresent(card -> namedBy.putIfAbsent(next.getUserId(), card.firstName()));
            }
        }
        Set<Long> contacts = contactMembers(viewerId);
        Set<String> skills = own.stack().stream().map(NetworkSuggestionService::plain).collect(Collectors.toSet());
        String city = plain(own.city());
        return members.findByActiveTrue().stream().filter(member -> !excluded.contains(member.getUserId()))
            .map(member -> network.liveCard(member).map(card -> new Candidate(card, contacts.contains(member.getUserId()),
                followedBy.getOrDefault(member.getUserId(), 0),
                namedBy.get(member.getUserId()), card.stack().stream().filter(skill -> skills.contains(plain(skill))).toList(),
                !city.isEmpty() && city.equals(plain(card.city())))))
            .flatMap(Optional::stream)
            .filter(candidate -> candidate.score() > 0)
            .sorted(Comparator.comparingInt(Candidate::score).reversed().thenComparing(candidate -> plain(candidate.card().lastName())))
            .limit(SHOWN)
            .map(candidate -> new NetworkDtos.Suggestion(candidate.card(), reason(candidate), candidate.followedBy(),
                candidate.followedByName(), candidate.sharedSkills()))
            .toList();
    }

    /**
     * The accounts among the viewer's workspace contacts, matched on the email each account signs in with; the contact is
     * never told.
     */
    private Set<Long> contactMembers(Long viewerId) {
        Set<String> emails = users.findById(viewerId).flatMap(user -> freelances.findByEmail(user.getEmail()))
            .map(freelance -> contactRepository.findByFreelanceId(freelance.getId())).orElse(List.of()).stream()
            .map(Contact::getEmail).filter(Objects::nonNull).map(email -> email.strip().toLowerCase(Locale.ROOT)).filter(email -> !email.isEmpty())
            .collect(Collectors.toSet());
        return emails.isEmpty() ? Set.of() : users.findByEmailsIgnoringCase(emails).stream().map(User::getId).collect(Collectors.toSet());
    }

    private static NetworkDtos.SuggestionReason reason(Candidate candidate) {
        if (candidate.contact()) {
            return NetworkDtos.SuggestionReason.CONTACT;
        }
        if (candidate.followedBy() > 0) {
            return NetworkDtos.SuggestionReason.FOLLOWED_BY;
        }
        return candidate.sharedSkills().isEmpty() ? NetworkDtos.SuggestionReason.SAME_CITY : NetworkDtos.SuggestionReason.SAME_STACK;
    }

    private static String plain(String text) {
        return text == null ? "" : Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
