package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.NetworkPollVote;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import dev.swirlit.indezy.repository.NetworkPollVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Votes in polls (see docs/features.md#member-network): once a member, who may change their choice while the poll is open. */
@Service
@RequiredArgsConstructor
public class NetworkPollService {

    private final NetworkPostService posts;
    private final NetworkPollVoteRepository votes;
    private final Clock clock;

    /** Votes for a choice of a poll the reader can read, or changes their vote, while it is open. */
    @Transactional
    public NetworkDtos.Post vote(Long userId, Long postId, int choice) {
        NetworkPost post = posts.readable(userId, postId);
        if (post.getKind() != NetworkPostKind.POLL) {
            throw new InvalidStateException("Only a poll takes votes");
        }
        if (!clock.instant().isBefore(post.getPollEndsAt())) {
            throw new InvalidStateException("This poll is closed");
        }
        if (choice < 0 || choice >= NetworkPolls.choices(post).size()) {
            throw new IllegalArgumentException("No such choice");
        }
        NetworkPollVote vote = votes.findByPostIdAndUserId(postId, userId).orElseGet(NetworkPollVote::new);
        vote.setPostId(postId);
        vote.setUserId(userId);
        vote.setChoice(choice);
        votes.save(vote);
        return posts.reread(post, userId);
    }
}
