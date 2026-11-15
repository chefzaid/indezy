package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.GlobalSearchResultDto.Hit;
import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The member network in the global search (see docs/features.md#keyboard-shortcuts): the members whose card shows every word
 * typed, by what it shows only, and the latest posts holding every word, but those of members blocked either way, away or
 * private to the reader;
 * nothing while the network is off or the account is not a member.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NetworkSearchService {

    /** How many of the latest posts are searched. */
    static final int POSTS_SEARCHED = 1000;

    private final FeatureFlagService flags;
    private final NetworkService network;
    private final NetworkPostService posts;
    private final NetworkMemberRepository members;
    private final NetworkPostRepository postRepository;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkFollowService follows;

    /** What the network holds for these words: members, then posts, at most {@code max} of each; nothing for no account. */
    public Found search(Long userId, List<String> words, int max) {
        if (userId == null || !flags.isEnabled(FeatureFlag.SOCIAL_NETWORK) || members.findByUserId(userId).filter(NetworkMember::isActive).isEmpty()) {
            return Found.NOTHING;
        }
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        List<Hit> memberHits = members.findByActiveTrue().stream()
            .filter(member -> !member.getUserId().equals(userId) && !blocked.contains(member.getUserId()))
            .flatMap(member -> network.liveCard(member).map(card -> new Seen(member.getId(), card)).stream())
            .sorted(Comparator.comparing((Seen seen) -> seen.card().lastName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .map(seen -> memberHit(words, seen)).filter(Objects::nonNull).limit(max).toList();
        Set<Long> unread = new HashSet<>(blocked);
        unread.addAll(follows.locked(userId));
        Map<Long, Optional<NetworkDtos.MemberCard>> authors = new HashMap<>();
        List<Hit> postHits = postRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, POSTS_SEARCHED)).stream()
            .filter(post -> !unread.contains(post.getAuthorId()) && GlobalSearchService.containsAll(post.getText(), words))
            .map(post -> authors.computeIfAbsent(post.getAuthorId(), posts::authorCard).map(author -> postHit(words, post, author)))
            .flatMap(Optional::stream).limit(max).toList();
        return new Found(memberHits, postHits);
    }

    /** The member when one field of their card holds every word: the name, the handle, the headline, a skill, the city. */
    private static Hit memberHit(List<String> words, Seen seen) {
        NetworkDtos.MemberCard card = seen.card();
        String name = name(card);
        List<String> fields = Stream.concat(Stream.of(name, card.handle(), card.headline(), card.city()), card.stack().stream())
            .filter(Objects::nonNull).toList();
        for (int i = 0; i < fields.size(); i++) {
            if (GlobalSearchService.containsAll(fields.get(i), words)) {
                String excerpt = i < 2 ? null : fields.get(i);
                return new Hit(seen.id(), name, "@" + card.handle(), excerpt, null, card.handle());
            }
        }
        return null;
    }

    private static Hit postHit(List<String> words, NetworkPost post, NetworkDtos.MemberCard author) {
        return new Hit(post.getId(), name(author), "@" + author.handle(), GlobalSearchService.excerpt(post.getText(), words.getFirst()),
            null, author.handle());
    }

    private static String name(NetworkDtos.MemberCard card) {
        return (Objects.toString(card.firstName(), "") + " " + Objects.toString(card.lastName(), "")).strip();
    }

    /** A member's card, with the id of their membership. */
    private record Seen(Long id, NetworkDtos.MemberCard card) {
    }

    /** The members and the posts found. */
    public record Found(List<Hit> members, List<Hit> posts) {

        static final Found NOTHING = new Found(List.of(), List.of());

        public Found {
            members = List.copyOf(members);
            posts = List.copyOf(posts);
        }
    }
}
