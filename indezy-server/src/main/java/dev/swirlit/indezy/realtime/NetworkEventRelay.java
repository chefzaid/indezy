package dev.swirlit.indezy.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

/**
 * Carries the network's events to every replica (see docs/architecture.md): on PostgreSQL each event goes out as a
 * {@code NOTIFY} on one channel that every replica listens to on a connection of its own, and each replica tells its own
 * streams; on another database (the tests' H2) the replica tells its own streams at once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NetworkEventRelay {

    static final String CHANNEL = "indezy_network";
    private static final Duration RETRY = Duration.ofSeconds(5);
    private static final int WAIT_MS = 10_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** One event and the members it is for, as it travels between replicas. */
    record Envelope(List<Long> to, NetworkEvent event) {
    }

    private final NetworkEventHub hub;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    /** Spring Boot's task executor, found by this name among the executors. */
    private final TaskExecutor applicationTaskExecutor;
    private volatile boolean postgres;
    private volatile boolean running;

    /** Starts listening once the application is ready, on PostgreSQL. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        postgres = Boolean.TRUE.equals(jdbcTemplate.execute((Connection connection) ->
            "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
        if (postgres && !running) {
            running = true;
            Thread listener = new Thread(this::listen, "network-events");
            listener.setDaemon(true);
            listener.start();
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        running = false;
    }

    /** Sends the event to the members, on every replica; out of the caller's thread and transaction on PostgreSQL. */
    public void send(List<Long> to, NetworkEvent event) {
        if (to.isEmpty()) {
            return;
        }
        if (!postgres) {
            hub.deliver(to, event);
            return;
        }
        String payload = JSON.writeValueAsString(new Envelope(List.copyOf(to), event));
        applicationTaskExecutor.execute(() -> {
            try {
                jdbcTemplate.queryForObject("SELECT pg_notify(?, ?)", Object.class, CHANNEL, payload);
            } catch (RuntimeException e) {
                log.warn("A network event could not be sent: {}", e.getClass().getSimpleName());
            }
        });
    }

    /** Tells this replica's streams of one notification's event; one that cannot be read is dropped. */
    void received(String payload) {
        try {
            Envelope envelope = JSON.readValue(payload, Envelope.class);
            hub.deliver(envelope.to(), envelope.event());
        } catch (RuntimeException e) {
            log.warn("A network event could not be read: {}", e.getClass().getSimpleName());
        }
    }

    /** Listens on a connection of its own while the application runs, opening another after a failure. */
    private void listen() {
        while (running) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                statement.execute("LISTEN " + CHANNEL);
                PGConnection notices = connection.unwrap(PGConnection.class);
                log.info("Listening to the network's events");
                while (running) {
                    pump(notices);
                }
            } catch (SQLException | RuntimeException e) {
                log.warn("Listening to the network's events stopped, again in {}s: {}", RETRY.toSeconds(), e.getClass().getSimpleName());
                pause();
            }
        }
    }

    /** Waits up to 10 seconds for notifications, and tells of each. */
    void pump(PGConnection notices) throws SQLException {
        PGNotification[] received = notices.getNotifications(WAIT_MS);
        if (received != null) {
            for (PGNotification notification : received) {
                received(notification.getParameter());
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(RETRY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
