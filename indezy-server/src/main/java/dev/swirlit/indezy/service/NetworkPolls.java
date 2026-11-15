package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkPollVote;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import dev.swirlit.indezy.repository.NetworkPollVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Polls (see docs/features.md#member-network): two to four choices, open one to seven days; their readers see the votes
 * once they voted, the poll closed or it is theirs.
 */
@Component
@RequiredArgsConstructor
public class NetworkPolls {

    private final NetworkPollVoteRepository votes;
    private final Clock clock;

    /** Sets a new poll's choices and closing time on its post. */
    void open(NetworkPost post, NetworkDtos.NewPoll poll) {
        if (poll == null) {
            throw new IllegalArgumentException("A poll has two to four choices");
        }
        List<String> choices = poll.choices().stream().map(choice -> choice.replaceAll("\\s+", " ").strip()).toList();
        if (choices.stream().distinct().count() < choices.size()) {
            throw new IllegalArgumentException("A poll's choices differ");
        }
        post.setPollChoices(String.join("\n", choices));
        post.setPollEndsAt(clock.instant().plus(Duration.ofDays(poll.days())));
    }

    /** The polls among these posts as the reader sees them, by post. */
    Map<Long, NetworkDtos.Poll> views(Collection<NetworkPost> shown, Long readerId) {
        List<NetworkPost> polls = shown.stream().filter(post -> post.getKind() == NetworkPostKind.POLL && post.getPollChoices() != null).toList();
        if (polls.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<NetworkPollVote>> cast = votes.findByPostIdIn(polls.stream().map(NetworkPost::getId).toList()).stream()
            .collect(Collectors.groupingBy(NetworkPollVote::getPostId));
        Instant now = clock.instant();
        Map<Long, NetworkDtos.Poll> views = new HashMap<>();
        for (NetworkPost post : polls) {
            List<NetworkPollVote> pollVotes = cast.getOrDefault(post.getId(), List.of());
            Integer mine = pollVotes.stream().filter(vote -> vote.getUserId().equals(readerId)).map(NetworkPollVote::getChoice).findFirst()
                .orElse(null);
            boolean closed = !now.isBefore(post.getPollEndsAt());
            boolean shownVotes = mine != null || closed || post.getAuthorId().equals(readerId);
            List<String> choices = choices(post);
            List<NetworkDtos.PollChoice> counted = IntStream.range(0, choices.size()).mapToObj(index -> new NetworkDtos.PollChoice(
                choices.get(index), shownVotes ? (int) pollVotes.stream().filter(vote -> vote.getChoice() == index).count() : null)).toList();
            views.put(post.getId(), new NetworkDtos.Poll(counted, post.getPollEndsAt(), closed, mine, shownVotes ? pollVotes.size() : null));
        }
        return views;
    }

    static List<String> choices(NetworkPost post) {
        return post.getPollChoices() == null ? List.of() : Arrays.asList(post.getPollChoices().split("\n"));
    }
}
