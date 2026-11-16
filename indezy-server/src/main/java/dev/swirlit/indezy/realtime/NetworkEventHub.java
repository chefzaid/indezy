package dev.swirlit.indezy.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The streams open on this replica, by member: each page that listens holds one, kept 15 minutes before the page opens
 * another, with a heartbeat every 25 seconds so proxies keep it open; a member holds 5 at most, the oldest closed first.
 */
@Component
@Slf4j
public class NetworkEventHub {

    static final Duration TIMEOUT = Duration.ofMinutes(15);
    static final int MAX_PER_MEMBER = 5;

    private final Map<Long, Deque<SseEmitter>> streams = new ConcurrentHashMap<>();

    /** Opens a stream for the member, told at once that it is open. */
    public SseEmitter open(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        Deque<SseEmitter> mine = streams.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        mine.addLast(emitter);
        while (mine.size() > MAX_PER_MEMBER) {
            SseEmitter oldest = mine.pollFirst();
            if (oldest != null) {
                oldest.complete();
            }
        }
        emitter.onCompletion(() -> close(userId, emitter));
        emitter.onTimeout(() -> close(userId, emitter));
        emitter.onError(error -> close(userId, emitter));
        send(userId, emitter, SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON));
        return emitter;
    }

    /** Tells each member's open streams on this replica. */
    public void deliver(Collection<Long> userIds, NetworkEvent event) {
        for (Long userId : userIds) {
            Deque<SseEmitter> mine = streams.get(userId);
            if (mine != null) {
                mine.forEach(emitter -> send(userId, emitter, SseEmitter.event().name(event.type()).data(event, MediaType.APPLICATION_JSON)));
            }
        }
    }

    /** How many streams are open on this replica. */
    public int streamCount() {
        return streams.values().stream().mapToInt(Deque::size).sum();
    }

    @Scheduled(initialDelayString = "PT25S", fixedDelayString = "PT25S")
    public void heartbeat() {
        streams.forEach((userId, mine) -> mine.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("ping"))));
    }

    /** Closes every stream before the replica stops, so that pages reconnect to another at once. */
    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        streams.values().forEach(mine -> mine.forEach(SseEmitter::complete));
        streams.clear();
    }

    private void send(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("A stream of member {} is gone: {}", userId, e.getClass().getSimpleName());
            close(userId, emitter);
        }
    }

    private void close(Long userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, mine) -> {
            mine.remove(emitter);
            return mine.isEmpty() ? null : mine;
        });
    }
}
