package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.NetworkComment;
import dev.swirlit.indezy.model.NetworkHeld;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkHeldRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The spam score of a post or a comment (see docs/security.md#member-network): points for many links, links from a
 * member who joined in the last day, the same text as one of the author's in the last day, many mentions, or shouting.
 * At {@value #HOLD_AT} points or more the text is held for an administrator instead of published.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NetworkSpamFilter {

    static final int HOLD_AT = 3;
    static final int MANY_LINKS = 3;
    static final int MANY_MENTIONS = 5;
    static final int SHOUTING_LETTERS = 30;
    static final Duration RECENT = Duration.ofDays(1);
    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern MENTION = Pattern.compile("(?<![\\p{L}\\p{N}_@.])@[a-z][a-z0-9_]{2,29}");

    /** Why a text scores. */
    public enum Reason {
        /** {@value #MANY_LINKS} links or more: 2 points. */
        LINKS(2),
        /** A link from a member who joined in the last day: 1 point. */
        NEW_MEMBER_LINK(1),
        /** The same text as one of the author's posts or comments of the last day: 3 points. */
        REPEATED(3),
        /** {@value #MANY_MENTIONS} mentions or more: 1 point. */
        MENTIONS(1),
        /** Mostly capital letters: 1 point. */
        SHOUTING(1);

        private final int points;

        Reason(int points) {
            this.points = points;
        }

        public int points() {
            return points;
        }
    }

    private final NetworkMemberRepository members;
    private final NetworkPostRepository posts;
    private final NetworkCommentRepository comments;
    private final NetworkHeldRepository held;
    private final Clock clock;

    /** Holds the text when it scores {@value #HOLD_AT} or more; answers what holds it, none when it goes out. */
    Optional<Long> hold(Long authorId, Long postId, Long parentId, String text) {
        Set<Reason> reasons = reasons(authorId, text);
        if (reasons.stream().mapToInt(Reason::points).sum() < HOLD_AT) {
            return Optional.empty();
        }
        NetworkHeld kept = new NetworkHeld();
        kept.setAuthorId(authorId);
        kept.setPostId(postId);
        kept.setParentId(parentId);
        kept.setText(text);
        kept.setReasons(reasons.stream().map(Enum::name).collect(Collectors.joining(",")));
        held.save(kept);
        log.info("Held a {} of member {} for review: {}", postId == null ? "post" : "comment", authorId, kept.getReasons());
        return Optional.of(kept.getId());
    }

    private Set<Reason> reasons(Long authorId, String text) {
        Instant now = clock.instant();
        Instant joined = members.findByUserId(authorId).map(NetworkMember::getJoinedAt).orElse(now);
        LocalDateTime since = LocalDateTime.ofInstant(now.minus(RECENT), ZoneId.systemDefault());
        List<String> recent = Stream.of(
            posts.findByAuthorIdAndCreatedAtAfter(authorId, since).stream().map(NetworkPost::getText),
            comments.findByAuthorIdAndCreatedAtAfter(authorId, since).stream().map(NetworkComment::getText),
            held.findByAuthorIdAndCreatedAtAfter(authorId, since).stream().map(NetworkHeld::getText)).flatMap(s -> s).toList();
        return score(text, joined.isAfter(now.minus(RECENT)), recent);
    }

    /** The reasons the text scores, given whether its author joined in the last day and their texts of the last day. */
    static Set<Reason> score(String text, boolean newMember, List<String> recent) {
        Set<Reason> reasons = EnumSet.noneOf(Reason.class);
        int links = count(LINK.matcher(text));
        if (links >= MANY_LINKS) {
            reasons.add(Reason.LINKS);
        }
        if (links > 0 && newMember) {
            reasons.add(Reason.NEW_MEMBER_LINK);
        }
        String normalized = normalized(text);
        if (recent.stream().map(NetworkSpamFilter::normalized).anyMatch(normalized::equals)) {
            reasons.add(Reason.REPEATED);
        }
        if (count(MENTION.matcher(text)) >= MANY_MENTIONS) {
            reasons.add(Reason.MENTIONS);
        }
        List<Integer> letters = new ArrayList<>();
        text.codePoints().filter(Character::isLetter).forEach(letters::add);
        long capitals = letters.stream().filter(Character::isUpperCase).count();
        if (letters.size() >= SHOUTING_LETTERS && capitals * 10 >= letters.size() * 7L) {
            reasons.add(Reason.SHOUTING);
        }
        return reasons;
    }

    private static int count(Matcher matcher) {
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    private static String normalized(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
