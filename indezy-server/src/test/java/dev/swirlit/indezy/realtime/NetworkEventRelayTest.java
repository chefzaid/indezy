package dev.swirlit.indezy.realtime;

import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkEventRelayTest {

    private final NetworkEventHub hub = mock(NetworkEventHub.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NetworkEventRelay relay = new NetworkEventRelay(hub, dataSource, jdbcTemplate, new SyncTaskExecutor());
    private final NetworkEvent message = new NetworkEvent(NetworkEvent.MESSAGE, 4L, null);

    @Test
    void tellsThisReplicaAtOnceOnAnotherDatabase() {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(false);
        relay.start();
        relay.send(List.of(2L, 3L), message);
        relay.send(List.of(), message);
        verify(hub).deliver(List.of(2L, 3L), message);
        verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(Object.class), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void notifiesEveryReplicaOnPostgresAndTellsTheStreamsOfWhatItHears() throws Exception {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(true);
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));
        relay.start();
        relay.stop();
        relay.send(List.of(2L), message);
        verify(jdbcTemplate).queryForObject("SELECT pg_notify(?, ?)", Object.class, NetworkEventRelay.CHANNEL,
            "{\"to\":[2],\"event\":{\"type\":\"message\",\"conversationId\":4}}");
        verify(hub, never()).deliver(any(), any());
        when(jdbcTemplate.queryForObject(any(String.class), eq(Object.class), any(), any())).thenThrow(new IllegalStateException("gone"));
        relay.send(List.of(2L), message);

        PGConnection connection = mock(PGConnection.class);
        PGNotification heard = mock(PGNotification.class);
        when(heard.getParameter()).thenReturn("{\"to\":[2,3],\"event\":{\"type\":\"typing\",\"conversationId\":4,\"name\":\"Léo\"}}");
        PGNotification broken = mock(PGNotification.class);
        when(broken.getParameter()).thenReturn("not json");
        when(connection.getNotifications(10_000)).thenReturn(new PGNotification[] {heard, broken}, (PGNotification[]) null);
        relay.pump(connection);
        relay.pump(connection);
        verify(hub).deliver(List.of(2L, 3L), new NetworkEvent(NetworkEvent.TYPING, 4L, "Léo"));
    }
}
