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
     * The members to mention for what was typed after an @: those whose handle, first or last name starts with it, each
     * word typed starting one of them ("bob mar" finds Bob Martin), accents and case aside; the members the viewer follows
     * first, then by last name; five at most, never the viewer nor a member either one blocks.
     */
    @Transactional(readOnly = true)
    public List<NetworkDtos.MemberCard> mentions(Long viewerId, String typed) {
        network.requireMember(viewerId);
        List<String> words = words(Optional.ofNullable(typed).orElse("").strip().replaceFirst("^@", ""));
        if (words.isEmpty()) {
            return List.of();
        }
        Set<Long> followed = followedBy(viewerId);
        return visible(viewerId).stream()
            .filter(hit -> words.stream().allMatch(word -> namedBy(hit, word)))
            .sorted(Comparator.comparing((Found hit) -> !followed.contains(hit.member().getUserId()))
                .thenComparing(hit -> plain(hit.card().lastName())).thenComparing(Found::handle))
            .limit(MENTIONED).map(Found::card).toList();
    }

    /**
     * The network's search for members: those every word typed names, by the start of their handle, first or last name, a
     * skill or their city, or within their headline, on what their cards show only, accents and case aside. Those named by
     * name first (the whole name first of all), then those the viewer follows, then by last name; 20 a page, never the
     * viewer nor a member either one blocks.
     */
    @Transactional(readOnly = true)
    public NetworkDtos.Directory find(Long viewerId, String typed, int page) {
        return find(viewerId, typed, page, new Search(null, null, null, null, page));
    }

    /** The search for members, narrowed by a city, an availability and remote work, as the directory's chips are. */
    @Transactional(readOnly = true)
    public NetworkDtos.Directory find(Long viewerId, String typed, int page, Search filters) {
        network.requireMember(viewerId);
        String whole = plain(Optional.ofNullable(typed).orElse("").replaceFirst("^[@#]", ""));
        List<String> words = words(whole);
        int asked = Math.max(0, page);
        if (words.isEmpty()) {
            return new NetworkDtos.Directory(List.of(), 0, asked, PAGE_SIZE, List.of(), List.of(), List.of());
        }
        Set<Long> followed = followedBy(viewerId);
        String city = plain(filters.city());
        List<Found> found = visible(viewerId).stream()
            .filter(hit -> words.stream().allMatch(word -> namedBy(hit, word) || describedBy(hit, word)))
            .filter(hit -> city.isEmpty() || (hit.card().city() != null && plain(hit.card().city()).contains(city)))
            .filter(hit -> filters.availability() == null || hit.card().availability() == filters.availability())
            .filter(hit -> filters.remote() == null || hit.card().remote() == filters.remote())
            .sorted(Comparator.comparingInt((Found hit) -> rank(hit, whole, words))
                .thenComparing(hit -> !followed.contains(hit.member().getUserId()))
                .thenComparing(hit -> plain(hit.card().lastName())).thenComparing(Found::handle))
            .toList();
        List<Found> shown = found.stream().skip((long) asked * PAGE_SIZE).limit(PAGE_SIZE).toList();
        List<String> following = shown.stream().filter(hit -> followed.contains(hit.member().getUserId())).map(Found::handle).toList();
        List<String> requested = shown.stream()
            .filter(hit -> requests.findByRequesterIdAndMemberId(viewerId, hit.member().getUserId()).isPresent()).map(Found::handle).toList();
        List<String> privateCards = shown.stream().filter(hit -> hit.member().isPrivate()).map(Found::handle).toList();
        return new NetworkDtos.Directory(shown.stream().map(Found::card).toList(), found.size(), asked, PAGE_SIZE, following, requested,
            privateCards);
    }

    /** The members in the network the viewer may see, with the cards they read: neither the viewer nor one blocked either way. */
    private List<Found> visible(Long viewerId) {
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(viewerId));
        return members.findByActiveTrue().stream()
            .filter(member -> !member.getUserId().equals(viewerId) && !blocked.contains(member.getUserId()))
            .flatMap(member -> network.liveCard(member).map(card -> new Found(member, card)).stream())
            .toList();
    }

    private Set<Long> followedBy(Long viewerId) {
        Set<Long> followed = new HashSet<>();
        follows.findByFollowerId(viewerId).forEach(follow -> followed.add(follow.getFollowedId()));
        return followed;
    }

    /** Whether a word starts the member's handle, first or last name (a word of it, for a compound name). */
    private static boolean namedBy(Found hit, String word) {
        return plain(hit.handle()).startsWith(word) || startsAWord(hit.card().firstName(), word) || startsAWord(hit.card().lastName(), word);
    }

    /** Whether a word starts one of the card's skills or its city, or is in its headline. */
    private static boolean describedBy(Found hit, String word) {
        return hit.card().stack().stream().anyMatch(skill -> startsAWord(skill, word)) || startsAWord(hit.card().city(), word) ||
            plain(hit.card().headline()).contains(word);
    }

    /** 0 for the whole name (or the handle) typed, 1 for a name every word starts, 2 for a member found by their card. */
    private static int rank(Found hit, String whole, List<String> words) {
        String name = plain(hit.card().firstName() + " " + hit.card().lastName());
        String reversed = plain(hit.card().lastName() + " " + hit.card().firstName());
        if (name.startsWith(whole) || reversed.startsWith(whole) || plain(hit.handle()).startsWith(whole)) {
            return 0;
        }
        return words.stream().allMatch(word -> namedBy(hit, word)) ? 1 : 2;
    }

    private static boolean startsAWord(String text, String word) {
        String plain = plain(text);
        return plain.startsWith(word) || plain.contains(" " + word) || plain.contains("-" + word);
    }

    /** The words typed, plain, one-letter ones aside but for a lone word. */
    private static List<String> words(String typed) {
        List<String> words = List.of(plain(typed).split("[\\s,]+")).stream().filter(word -> !word.isEmpty()).toList();
        return words.size() > 1 ? words.stream().filter(word -> word.length() > 1).toList() : words;
    }

    /** Lower case, accents and surrounding spaces dropped, to compare as people type. */
    private static String plain(String text) {
        return text == null ? "" : Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
