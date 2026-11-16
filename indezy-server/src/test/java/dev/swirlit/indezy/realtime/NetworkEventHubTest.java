package dev.swirlit.indezy.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkEventHubTest {

    private final NetworkEventHub hub = new NetworkEventHub();

    @Test
    void keepsFiveStreamsAMemberAtMostAndDropsThoseGone() {
        List<SseEmitter> bob = new ArrayList<>();
        for (int i = 0; i <= NetworkEventHub.MAX_PER_MEMBER; i++) {
            bob.add(hub.open(2L));
        }
        hub.open(3L);
        assertThat(hub.streamCount()).isEqualTo(NetworkEventHub.MAX_PER_MEMBER + 1);
        assertThat(bob.getFirst().getTimeout()).isEqualTo(NetworkEventHub.TIMEOUT.toMillis());

        // The oldest was closed: sending to it fails, and a closed stream is dropped on the next send.
        bob.get(1).complete();
        hub.deliver(Set.of(2L, 9L), new NetworkEvent(NetworkEvent.MESSAGE, 4L, null));
        assertThat(hub.streamCount()).isEqualTo(NetworkEventHub.MAX_PER_MEMBER);
        hub.heartbeat();
        assertThat(hub.streamCount()).isEqualTo(NetworkEventHub.MAX_PER_MEMBER);

        hub.closeAll();
        assertThat(hub.streamCount()).isZero();
        hub.heartbeat();
    }
}
