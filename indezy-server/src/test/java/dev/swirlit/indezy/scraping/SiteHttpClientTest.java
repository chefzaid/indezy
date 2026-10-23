package dev.swirlit.indezy.scraping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The collection's HTTP rules, against a local server standing in for a job board. */
class SiteHttpClientTest {

    private static final String DOMAIN = "127.0.0.1";

    private HttpServer server;
    private String base;
    private final List<Duration> waits = new ArrayList<>();
    private final Map<String, String> seenHeaders = new ConcurrentHashMap<>();
    private volatile String robots = "User-agent: *\nDisallow: /private\n";
    private volatile int robotsStatus = 200;
    private volatile String robotsRedirect;
    private SiteHttpClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", exchange -> {
            if (robotsRedirect != null) {
                redirect(exchange, robotsRedirect);
            } else {
                respond(exchange, robotsStatus, robots, "text/plain");
            }
        });
        server.createContext("/robots-v2.txt", exchange -> respond(exchange, 200, "User-agent: *\nDisallow: /page\n", "text/plain"));
        server.createContext("/page", exchange -> {
            seenHeaders.put("user-agent", String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=ISO-8859-1");
            byte[] body = "Café".getBytes(StandardCharsets.ISO_8859_1);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/gzip", exchange -> {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
            }
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, compressed.size());
            try (OutputStream out = exchange.getResponseBody()) {
                compressed.writeTo(out);
            }
        });
        server.createContext("/redirect", exchange -> redirect(exchange, "/page"));
        server.createContext("/away", exchange -> redirect(exchange, "http://example.com/jobs"));
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.createContext("/moved/", exchange -> {
            exchange.getResponseHeaders().add("Location", "/page");
            exchange.sendResponseHeaders(Integer.parseInt(exchange.getRequestURI().getPath().substring("/moved/".length())), -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> respond(exchange, 404, "gone", "text/plain"));
        server.createContext("/big", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                byte[] chunk = new byte[1024 * 1024];
                for (int i = 0; i <= SiteHttpClient.MAX_BODY_BYTES / chunk.length; i++) {
                    out.write(chunk);
                }
            } catch (IOException e) {
                // The client stops reading at the cap and closes the connection.
            }
        });
        server.createContext("/api", exchange -> {
            seenHeaders.put("content-type", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
            respond(exchange, 200, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), "application/json");
        });
        server.start();
        base = "http://" + DOMAIN + ":" + server.getAddress().getPort();
        OfferProperties properties = new OfferProperties(true, Duration.ofMinutes(5), Duration.ofSeconds(2), 10,
            Duration.ofHours(1), 90, "IndezyBot/test", false, "http://127.0.0.1");
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        client = new SiteHttpClient(properties, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
            clock, waits::add);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetchesAPageInItsCharsetWithTheIndezyUserAgent() throws Exception {
        assertThat(client.fetcherFor(DOMAIN).get(base + "/page")).isEqualTo("Café");
        assertThat(seenHeaders).containsEntry("user-agent", "IndezyBot/test");
    }

    @Test
    void unzipsCompressedAnswersAndPostsJson() throws Exception {
        PageFetcher fetcher = client.fetcherFor(DOMAIN);

        assertThat(fetcher.get(base + "/gzip")).isEqualTo("{\"ok\":true}");
        assertThat(fetcher.postJson(base + "/api", "{\"page\":1}")).isEqualTo("{\"page\":1}");
        assertThat(seenHeaders).containsEntry("content-type", "application/json");
    }

    @Test
    void waitsTheDelayBetweenTwoRequestsToTheSameHost() throws Exception {
        PageFetcher fetcher = client.fetcherFor(DOMAIN);

        fetcher.get(base + "/page");
        fetcher.get(base + "/page");

        // robots.txt, then the first page and the second each wait their turn.
        assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @Test
    void followsRedirectsOnTheSiteOnly() throws Exception {
        PageFetcher fetcher = client.fetcherFor(DOMAIN);

        assertThat(fetcher.get(base + "/redirect")).isEqualTo("Café");
        assertThatThrownBy(() -> fetcher.get(base + "/away"))
            .isInstanceOf(SiteResponseException.class).hasMessageContaining("Refused to leave");
        assertThatThrownBy(() -> fetcher.postJson(base + "/redirect", "{}"))
            .isInstanceOf(SiteResponseException.class).hasMessageContaining("Unexpected redirect");
    }

    @Test
    void refusesOtherDomainsAndSchemes() {
        PageFetcher fetcher = client.fetcherFor("example.org");

        assertThatThrownBy(() -> fetcher.get(base + "/page")).isInstanceOf(SiteResponseException.class)
            .extracting(e -> ((SiteResponseException) e).getStatus()).isEqualTo(0);
        assertThatThrownBy(() -> client.fetcherFor(DOMAIN).get("ftp://127.0.0.1/file")).isInstanceOf(SiteResponseException.class);
    }

    @Test
    void readsAReadersSiteAndItsApiOnly() throws Exception {
        assertThat(client.fetcherFor(new ApiReader("example.org", Optional.of(DOMAIN))).get(base + "/page")).isEqualTo("Café");
        assertThat(client.fetcherFor(new ApiReader(DOMAIN, Optional.empty())).get(base + "/page")).isEqualTo("Café");
        assertThatThrownBy(() -> client.fetcherFor(new ApiReader("example.org", Optional.of("example.net"))).get(base + "/page"))
            .isInstanceOf(SiteResponseException.class).hasMessage("Refused to leave example.org and example.net for 127.0.0.1");
    }

    /** A reader of {@code domain} whose site's API is on {@code apiDomain}. */
    private record ApiReader(String domain, Optional<String> apiDomain) implements OfferReader {
        @Override
        public OfferSite site() {
            return OfferSite.KICKLOX;
        }

        @Override
        public void read(PageFetcher fetcher, OfferBatch batch) {
            // Only its domains are read here.
        }
    }

    @Test
    void readsOnlyTheHostsItsCheckAccepts() throws Exception {
        assertThat(client.fetcherFor(DOMAIN, host -> true).get(base + "/page")).isEqualTo("Café");
        // Refused before any request, its robots.txt included.
        assertThatThrownBy(() -> client.fetcherFor(DOMAIN, host -> false).get(base + "/page"))
            .isInstanceOf(SiteResponseException.class).hasMessage("Refused to read 127.0.0.1");
        assertThatThrownBy(() -> client.fetcherFor(DOMAIN, host -> false).get(base + "/redirect"))
            .isInstanceOf(SiteResponseException.class);
    }

    @Test
    void honoursRobotsTxt() {
        assertThatThrownBy(() -> client.fetcherFor(DOMAIN).get(base + "/private/jobs"))
            .isInstanceOf(SiteResponseException.class).hasMessageContaining("robots.txt disallows");
    }

    @Test
    void readsEverythingWithoutRobotsTxtAndNothingWhenItFails() throws Exception {
        robotsStatus = 404;
        assertThat(client.fetcherFor(DOMAIN).get(base + "/page")).isEqualTo("Café");

        SiteHttpClient failing = new SiteHttpClient(new OfferProperties(true, Duration.ofMinutes(5), Duration.ZERO, 10,
            Duration.ofHours(1), 90, "IndezyBot/test", false, "http://127.0.0.1"),
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), Clock.systemUTC(), waits::add);
        robotsStatus = 503;
        assertThatThrownBy(() -> failing.fetcherFor(DOMAIN).get(base + "/page"))
            .isInstanceOf(SiteResponseException.class).hasMessageContaining("robots.txt");
    }

    @Test
    void reportsErrorStatusesAndOversizedAnswers() {
        PageFetcher fetcher = client.fetcherFor(DOMAIN);

        assertThatThrownBy(() -> fetcher.get(base + "/missing")).isInstanceOf(SiteResponseException.class)
            .extracting(e -> ((SiteResponseException) e).getStatus()).isEqualTo(404);
        assertThatThrownBy(() -> fetcher.get(base + "/big")).isInstanceOf(IOException.class).hasMessageContaining("passes");
    }

    @Test
    void followsEveryRedirectStatusButNoLoop() throws Exception {
        PageFetcher fetcher = client.fetcherFor(DOMAIN);

        for (int status : new int[] {301, 303, 307, 308}) {
            assertThat(fetcher.get(base + "/moved/" + status)).isEqualTo("Café");
        }
        assertThatThrownBy(() -> fetcher.get(base + "/moved/304")).isInstanceOf(SiteResponseException.class)
            .extracting(e -> ((SiteResponseException) e).getStatus()).isEqualTo(304);
        assertThatThrownBy(() -> fetcher.get(base + "/loop")).isInstanceOf(SiteResponseException.class)
            .hasMessageContaining("Unexpected redirect");
    }

    @Test
    void readsQueriesAndHostsUnderTheDomainAndReportsPostErrors() throws Exception {
        PageFetcher fetcher = client.fetcherFor(DOMAIN);

        assertThat(fetcher.get(base + "/page?sort=date")).isEqualTo("Café");
        // Any host under the domain, as www.free-work.com under free-work.com.
        assertThat(client.fetcherFor("0.0.1").get(base + "/page")).isEqualTo("Café");
        assertThatThrownBy(() -> fetcher.get(base)).isInstanceOf(SiteResponseException.class);
        assertThatThrownBy(() -> fetcher.get("/page")).isInstanceOf(SiteResponseException.class).hasMessageContaining("Refused to leave");
        assertThatThrownBy(() -> fetcher.postJson(base + "/missing", "{}")).hasMessageStartingWith("POST /missing answered 404");
    }

    @Test
    void followsARobotsRedirectAndReadsNothingWhenItLoopsOrLeadsNowhere() throws Exception {
        robotsRedirect = "/robots-v2.txt";
        PageFetcher moved = clientAt(new MovingClock()).fetcherFor(DOMAIN);
        assertThat(moved.get(base + "/gzip")).isEqualTo("{\"ok\":true}");
        assertThatThrownBy(() -> moved.get(base + "/page")).hasMessageContaining("robots.txt disallows");

        robotsRedirect = "/robots.txt";
        assertThatThrownBy(() -> clientAt(new MovingClock()).fetcherFor(DOMAIN).get(base + "/gzip")).hasMessageContaining("robots.txt disallows");

        robotsRedirect = null;
        robotsStatus = 302;
        assertThatThrownBy(() -> clientAt(new MovingClock()).fetcherFor(DOMAIN).get(base + "/gzip")).hasMessageContaining("robots.txt disallows");
    }

    @Test
    void readsRobotsTxtAgainOnceItsCopyIsOld() throws Exception {
        MovingClock clock = new MovingClock();
        PageFetcher fetcher = clientAt(clock).fetcherFor(DOMAIN);
        fetcher.get(base + "/page");
        robots = "User-agent: *\nDisallow: /page\n";

        clock.advance(Duration.ofHours(1));
        assertThat(fetcher.get(base + "/page")).isEqualTo("Café");
        clock.advance(Duration.ofHours(12));
        assertThatThrownBy(() -> fetcher.get(base + "/page")).hasMessageContaining("robots.txt disallows");
        // An hour apart, the requests had no delay to wait.
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    private SiteHttpClient clientAt(Clock clock) {
        return new SiteHttpClient(new OfferProperties(true, Duration.ofMinutes(5), Duration.ofSeconds(2), 10, Duration.ofHours(1), 90,
            "IndezyBot/test", false, "http://127.0.0.1"), HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), clock, waits::add);
    }

    /** A clock the test moves forward. */
    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-10-19T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
