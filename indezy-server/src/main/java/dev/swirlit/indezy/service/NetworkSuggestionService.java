package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
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
 * followed by members the viewer follows, through the same ESN (both had an opportunity with it, only between members who
 * both allow it), sharing skills with the viewer's card, or in the same city; on what the cards show only.
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
    private final ProjectRepository projects;

    private record Candidate(NetworkDtos.MemberCard card, boolean contact, int followedBy, String followedByName, List<String> sharedSkills,
                             boolean sameCity, String esn) {

        int score() {
            return (contact ? 10 : 0) + followedBy * 3 + (esn != null ? 4 : 0) + sharedSkills.size() * 2 + (sameCity ? 1 : 0);
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
        // Suggestions by contacts only between members who both leave them on.
        Set<Long> contacts = viewer.isContactSuggestions() ? contactMembers(viewerId) : Set.of();
        Set<String> skills = own.stack().stream().map(NetworkSuggestionService::plain).collect(Collectors.toSet());
        String city = plain(own.city());
        Map<String, String> ownEsns = viewer.isEsnSuggestions() ? esns(viewerId) : Map.of();
        return members.findByActiveTrue().stream().filter(member -> !excluded.contains(member.getUserId()))
            .map(member -> network.liveCard(member).map(card -> new Candidate(card,
                member.isContactSuggestions() && contacts.contains(member.getUserId()),
                followedBy.getOrDefault(member.getUserId(), 0),
                namedBy.get(member.getUserId()), card.stack().stream().filter(skill -> skills.contains(plain(skill))).toList(),
                !city.isEmpty() && city.equals(plain(card.city())), sharedEsn(ownEsns, member))))
            .flatMap(Optional::stream)
            .filter(candidate -> candidate.score() > 0)
            .sorted(Comparator.comparingInt(Candidate::score).reversed().thenComparing(candidate -> plain(candidate.card().lastName())))
            .limit(SHOWN)
            .map(candidate -> new NetworkDtos.Suggestion(candidate.card(), reason(candidate), candidate.followedBy(),
                candidate.followedByName(), candidate.sharedSkills(), candidate.esn()))
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
        if (candidate.esn() != null) {
            return NetworkDtos.SuggestionReason.SAME_ESN;
        }
        return candidate.sharedSkills().isEmpty() ? NetworkDtos.SuggestionReason.SAME_CITY : NetworkDtos.SuggestionReason.SAME_STACK;
    }

    /** An ESN both went through, by its name in the viewer's workspace; none unless the member allows it too. */
    private String sharedEsn(Map<String, String> ownEsns, NetworkMember member) {
        if (ownEsns.isEmpty() || !member.isEsnSuggestions()) {
            return null;
        }
        return esns(member.getUserId()).keySet().stream().filter(ownEsns::containsKey).map(ownEsns::get).sorted().findFirst().orElse(null);
    }

    /**
     * The ESNs of the account's opportunities (the intermediary, or the client when it is not the final one), by their
     * SIREN when known, else their name, accents and case aside; each with the name the workspace gives it.
     */
    private Map<String, String> esns(Long userId) {
        Map<String, String> found = new HashMap<>();
        users.findById(userId).flatMap(user -> freelances.findByEmail(user.getEmail())).ifPresent(freelance ->
            projects.findByFreelanceId(freelance.getId()).forEach(project -> {
                Client esn = project.getMiddleman() != null ? project.getMiddleman() :
                    project.getClient() != null && Boolean.FALSE.equals(project.getClient().getIsFinal()) ? project.getClient() : null;
                if (esn != null && esn.getCompanyName() != null) {
                    String key = esn.getSiren() != null && !esn.getSiren().isBlank() ? "siren:" + esn.getSiren().strip() :
                        "name:" + plain(esn.getCompanyName());
                    found.putIfAbsent(key, esn.getCompanyName().strip());
                }
            }));
        return found;
    }

    private static String plain(String text) {
        return text == null ? "" : Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
