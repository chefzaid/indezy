package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.OfferBulkStateRequest;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.model.GeoPlace;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.GeoPlaceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.scraping.OfferNormalizer;
import dev.swirlit.indezy.scraping.OfferStore;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.service.OfferMentionIndexer;
import dev.swirlit.indezy.service.OfferRateBenchmarkService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClientException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offer feed under load (see docs/testing.md#search-load-test): 50,000 offers collected into PostgreSQL through
 * the real normalizer and store, a workspace with a profile and offers it saved, hid, read and noted, then the feed's
 * common searches twenty at a time, over HTTP. Fails when the slowest twentieth of the searches takes 300 ms or more,
 * and writes its timings to {@code target/load-test/report.md}. Only run on request against a disposable database:
 * Maven leaves the {@code load} tag out otherwise, and the offers are kept for the next run.
 */
@Tag("load")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=${INDEZY_LOAD_DATABASE_URL:jdbc:postgresql://127.0.0.1:55432/indezy_load}",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.datasource.username=${INDEZY_LOAD_DATABASE_USER:indezy}",
        "spring.datasource.password=${INDEZY_LOAD_DATABASE_PASSWORD:indezy}",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
        "spring.jpa.properties.hibernate.jdbc.batch_size=100",
        // What the database setup hook sets for the application's role (see docs/architecture.md#offer-search-at-scale).
        "spring.datasource.hikari.connection-init-sql=SET jit = off; SET random_page_cost = 1.1",
        "indezy.security.permit-all=false",
        "logging.level.org.springframework.web=INFO",
        "logging.level.org.springframework.security=INFO"
    })
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class OfferSearchLoadTest {

    private static final Logger LOG = LoggerFactory.getLogger(OfferSearchLoadTest.class);

    static final int OFFERS = 50_000;
    static final int CONCURRENCY = 20;
    /** Rounds of {@value #CONCURRENCY} requests; {@code -Dindezy.load.rounds} measures a slow build in fewer. */
    static final int ROUNDS = Integer.getInteger("indezy.load.rounds", 20);
    static final long TARGET_P95_MS = 300;
    /** Times each request is sent alone, one after the other, before the load: what it costs without waiting. */
    private static final int ALONE = 3;
    private static final int STORE_BATCH = 100;
    private static final long SEED = 20_261_021L;
    private static final Path REPORT = Path.of("target", "load-test", "report.md");

    /** One kind of request the feed sends, as its query string. */
    record Scenario(String name, String path) {
    }

    /** How long one request took, and whether it was answered. */
    record Sample(String scenario, long millis, boolean answered) {
    }

    /** The feed's common searches: what people type and pick most. */
    static final List<Scenario> SEARCHES = List.of(
        new Scenario("Newest, no filter", "/offers"),
        new Scenario("Keyword: java", "/offers?q=java"),
        new Scenario("Two keywords: kubernetes terraform", "/offers?q=kubernetes terraform"),
        new Scenario("Rare keyword: cobol", "/offers?q=cobol"),
        new Scenario("Keyword, rate, region, remote", "/offers?q=développeur&rateMin=600&regions=11&workModes=REMOTE&workModes=HYBRID"),
        new Scenario("Best match", "/offers?sort=MATCH"),
        new Scenario("Highest rate naming Kafka", "/offers?sort=RATE&skills=Kafka"),
        new Scenario("Within 30 km, nearest", "/offers?radiusKm=30&sort=DISTANCE"),
        new Scenario("Last 7 days, senior or expert", "/offers?publishedWithinDays=7&seniorities=SENIOR&seniorities=EXPERT"),
        new Scenario("Page 21", "/offers?page=20"),
        // The feed sends the total its first page counted when it reads another page or sort of the same filters.
        new Scenario("Page 21, total known", "/offers?page=20&total=34000"),
        new Scenario("Saved offers", "/offers?view=SAVED"),
        new Scenario("Unread offers matching the profile", "/offers?unseenOnly=true&matchesProfile=true"));

    /** What the feed asks beside a search: the sites with their counts, the skill suggestions and the market panel. */
    static final List<Scenario> PANELS = List.of(
        new Scenario("Sites with their open offers", "/offers/sites"),
        new Scenario("Skill suggestions: all", "/offers/skills"),
        new Scenario("Skill suggestions: \"ja\"", "/offers/skills?q=ja"),
        new Scenario("Market of a keyword search", "/offers/market?q=java"));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OfferNormalizer normalizer;

    @Autowired
    private OfferStore store;

    @Autowired
    private OfferRateBenchmarkService benchmarks;

    @Autowired
    private UserRepository users;

    @Autowired
    private GeoPlaceRepository places;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OfferMentionIndexer mentions;

    @Test
    void searchesStayFastWithFiftyThousandOffers() throws Exception {
        Duration seeding = seed();
        Account account = workspace();
        Duration recording = record();
        // Warm the server and the database cache, as an hour of real use would.
        SEARCHES.forEach(scenario -> time(account, scenario));
        List<Sample> alone = new ArrayList<>();
        for (int i = 0; i < ALONE; i++) {
            Stream.concat(SEARCHES.stream(), PANELS.stream()).forEach(scenario -> alone.add(time(account, scenario)));
        }
        List<Sample> searches = underLoad(account, SEARCHES);
        List<Sample> panels = underLoad(account, PANELS);

        String report = report(seeding, recording, alone, searches, panels);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report);
        LOG.info("Search load test\n{}", report);
        assertThat(failed(searches) + failed(panels)).as("requests not answered").isZero();
        assertThat(percentile(searches, 95)).as("95th percentile of the searches, in ms").isLessThan(TARGET_P95_MS);
    }

    /** Collects the offers the database still lacks, through the real normalizer and store; how long it took. */
    private Duration seed() {
        long start = System.nanoTime();
        long stored = count("select count(*) from job_offers where external_id like ?", LoadTestOffers.EXTERNAL_ID_PREFIX + "%");
        if (stored < OFFERS) {
            Instant now = Instant.now();
            Map<OfferSite, List<JobOffer>> bySite = new LinkedHashMap<>();
            for (LoadTestOffers.Collected collected : LoadTestOffers.generate(OFFERS, SEED, now)) {
                Optional<JobOffer> offer = normalizer.normalize(collected.site(), collected.offer(), now);
                offer.ifPresent(normalized -> {
                    List<JobOffer> batch = bySite.computeIfAbsent(collected.site(), site -> new ArrayList<>());
                    batch.add(normalized);
                    if (batch.size() == STORE_BATCH) {
                        store.save(collected.site(), List.copyOf(batch));
                        batch.clear();
                    }
                });
            }
            bySite.forEach((site, rest) -> store.save(site, rest));
            // A fifth of the stored offers has expired since: the feed keeps them until they are 90 days old.
            jdbc.update("update job_offers set expires_at = now() - interval '3 days' where external_id like ? and id % 5 = 0",
                LoadTestOffers.EXTERNAL_ID_PREFIX + "%");
            jdbc.execute("ANALYZE");
        }
        if (benchmarks.isEmpty()) {
            benchmarks.refresh();
        }
        return Duration.ofNanos(System.nanoTime() - start);
    }

    /**
     * A new workspace near Paris whose profile names Java, Spring Boot and Kubernetes at 650 € a day, which saved 200
     * offers, hid 100, read 1,300 and noted 30.
     */
    private Account workspace() {
        Account account = register();
        if (places.findById("|paris").isEmpty()) {
            GeoPlace paris = new GeoPlace();
            paris.setQueryKey("|paris");
            paris.setFound(true);
            paris.setCity("Paris");
            paris.setDepartmentCode("75");
            paris.setRegionCode("11");
            paris.setLatitude(48.8589);
            paris.setLongitude(2.3469);
            paris.setResolvedAt(Instant.now());
            places.save(paris);
        }
        users.findByEmail(account.email()).ifPresent(user -> {
            user.setSkills(List.of("Java", "Spring Boot", "Kubernetes"));
            user.setPosition("Développeur Java");
            user.setTargetDailyRate(650);
            user.setCity("Paris");
            users.save(user);
        });
        List<Long> ids = jdbc.queryForList("select id from job_offers where external_id like ? order by id limit 1300",
            Long.class, LoadTestOffers.EXTERNAL_ID_PREFIX + "%");
        for (int from = 0; from < ids.size(); from += OfferBulkStateRequest.MAX_OFFERS) {
            List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + OfferBulkStateRequest.MAX_OFFERS));
            boolean saved = from < 200;
            boolean hidden = !saved && from < 300;
            exchange(account, HttpMethod.PUT, "/offers/states", new OfferBulkStateRequest(chunk, saved ? true : null,
                hidden ? true : null, true));
        }
        for (Long id : ids.subList(0, 30)) {
            exchange(account, HttpMethod.PUT, "/offers/" + id + "/state",
                new OfferStateUpdateRequest(null, null, null, "Relancer le cabinet, rappel prévu jeudi"));
        }
        return account;
    }

    /** {@link #ROUNDS} rounds of {@value #CONCURRENCY} requests at once, taking the scenarios in turn. */
    private List<Sample> underLoad(Account account, List<Scenario> scenarios) throws Exception {
        Collection<Sample> samples = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> requests = new ArrayList<>();
            for (int i = 0; i < ROUNDS * CONCURRENCY; i++) {
                Scenario scenario = scenarios.get(i % scenarios.size());
                requests.add(pool.submit(() -> {
                    start.await();
                    samples.add(time(account, scenario));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> request : requests) {
                request.get(2, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
        return List.copyOf(samples);
    }

    /** Times one request; one not answered (an error, a timeout) is counted, not thrown, so the report is written. */
    private Sample time(Account account, Scenario scenario) {
        long start = System.nanoTime();
        boolean answered;
        try {
            answered = send(account, HttpMethod.GET, scenario.path(), null).getStatusCode() == HttpStatus.OK;
        } catch (RestClientException e) {
            answered = false;
        }
        return new Sample(scenario.name(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), answered);
    }

    private static long failed(List<Sample> samples) {
        return samples.stream().filter(sample -> !sample.answered()).count();
    }

    /** Records the profile's terms in every offer, as the scheduler does once a minute; how long it took. */
    private Duration record() {
        long start = System.nanoTime();
        mentions.indexed(new OfferMatch.Profile(List.of("Java", "Spring Boot", "Kubernetes"), null, null).terms());
        mentions.indexPending(Integer.MAX_VALUE);
        jdbc.execute("ANALYZE");
        return Duration.ofNanos(System.nanoTime() - start);
    }

    private String report(Duration seeding, Duration recording, List<Sample> alone, List<Sample> searches, List<Sample> panels) {
        StringBuilder report = new StringBuilder();
        report.append("| | |\n|---|---|\n")
            .append("| Database | ").append(jdbc.queryForObject("show server_version", String.class)).append(" |\n")
            .append("| Offers | ").append(count("select count(*) from job_offers")).append(", ")
            .append(count("select count(*) from job_offers where expires_at is null or expires_at > now()")).append(" open, ")
            .append(count("select count(distinct copy_group) from job_offers")).append(" missions on several sites |\n")
            .append("| Trigram index | ").append(count("select count(*) from pg_indexes where indexname = 'idx_job_offers_search_text_trgm'") > 0 ?
                "yes" : "no").append(" |\n")
            .append("| Server processors | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n")
            .append("| Seeding or reuse | ").append(seeding.toSeconds()).append(" s |\n")
            .append("| Mentions recorded (offers left to record, profile terms) | ").append(recording.toSeconds()).append(" s |\n")
            .append("| Searches | ").append(searches.size()).append(", ").append(CONCURRENCY).append(" at a time: p50 ")
            .append(percentile(searches, 50)).append(" ms, p95 ").append(percentile(searches, 95)).append(" ms, max ")
            .append(percentile(searches, 100)).append(" ms, ").append(failed(searches)).append(" not answered |\n\n");
        table(report, SEARCHES, alone, searches);
        report.append('\n');
        table(report, PANELS, alone, panels);
        return report.toString();
    }

    /** Each request's median alone, then its percentiles under load, in the order of {@code scenarios}. */
    private static void table(StringBuilder report, List<Scenario> scenarios, List<Sample> alone, List<Sample> underLoad) {
        report.append("| Request | Alone, median (ms) | p50 (ms) | p95 (ms) | max (ms) | Not answered |\n")
            .append("|---|---:|---:|---:|---:|---:|\n");
        Map<String, List<Sample>> byAlone = alone.stream().collect(Collectors.groupingBy(Sample::scenario));
        Map<String, List<Sample>> byLoad = underLoad.stream().collect(Collectors.groupingBy(Sample::scenario));
        for (Scenario scenario : scenarios) {
            List<Sample> timings = byLoad.get(scenario.name());
            report.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %d |%n", scenario.name(),
                percentile(byAlone.get(scenario.name()), 50), percentile(timings, 50), percentile(timings, 95),
                percentile(timings, 100), failed(timings)));
        }
    }

    /** The nearest-rank percentile of the timings. */
    static long percentile(List<Sample> samples, int percent) {
        List<Long> sorted = samples.stream().map(Sample::millis).sorted().toList();
        int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    private long count(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private Account register() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Charge");
        request.setLastName("Test");
        request.setEmail("load." + UUID.randomUUID() + "@example.com");
        request.setPassword("Password123!");
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/auth/register"), request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Account((String) response.getBody().get("token"), request.getEmail());
    }

    private void exchange(Account account, HttpMethod method, String path, Object body) {
        assertThat(send(account, method, path, body).getStatusCode()).as(method + " " + path).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> send(Account account, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    private record Account(String token, String email) {
    }
}
