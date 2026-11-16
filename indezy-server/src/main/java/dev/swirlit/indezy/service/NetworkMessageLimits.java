package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.repository.NetworkConversationRepository;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * How many messages a member may send in an hour, and how many conversations they may open in a day (see
 * docs/security.md#member-network): beyond, the request answers 429 with the time to wait. A suspended member sends none.
 */
@Component
@RequiredArgsConstructor
public class NetworkMessageLimits {

    static final int MESSAGES_PER_HOUR = 200;
    /** Many first messages are what spam looks like. */
    static final int CONVERSATIONS_PER_DAY = 20;

    private final NetworkLimits limits;
    private final NetworkMessageRepository messages;
    private final NetworkConversationRepository conversations;
    private final Clock clock;

    void message(Long userId) {
        limits.notSuspended(userId);
        if (messages.countBySenderIdAndCreatedAtAfter(userId, ago(Duration.ofHours(1))) >= MESSAGES_PER_HOUR) {
            throw new TooManyRequestsException("Too many messages this hour: try again later", Duration.ofHours(1));
        }
    }

    void conversation(Long userId) {
        limits.notSuspended(userId);
        if (conversations.countByCreatedByAndCreatedAtAfter(userId, ago(Duration.ofDays(1))) >= CONVERSATIONS_PER_DAY) {
            throw new TooManyRequestsException("Too many new conversations today: try again tomorrow", Duration.ofDays(1));
        }
    }

    /** That long ago, in the zone the rows' creation times are written in. */
    private LocalDateTime ago(Duration window) {
        return LocalDateTime.now(clock.withZone(ZoneId.systemDefault())).minus(window);
    }
}
