package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.enums.OfferSite;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the first page of every site the feed reads, live, through its reader and the collection's polite client, and
 * fails naming each site whose offers no longer yield a title, a link and a date (see docs/testing.md#reader-canaries).
 * The pages read from a failing site are kept under {@code target/reader-canary/} to update the test samples. Only the
 * scheduled canary job runs it: Maven leaves the {@code canary} tag out otherwise.
 */
@Tag("canary")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ReaderCanaryTest {

    private static final Logger LOG = LoggerFactory.getLogger(ReaderCanaryTest.class);

    /** Sites whose list dates no offer: Indezy dates them when it first sees them. */
    static final Set<OfferSite> UNDATED = Set.of(OfferSite.LITTLE_BIG_CONNECTION, OfferSite.SIXIEME_HOMME, OfferSite.OUIDESK);
    private static final Path SAMPLES = Path.of("target", "reader-canary");

    @Autowired
    private List<OfferReader> readers;

    @Autowired
    private SiteHttpClient http;

    @Test
    void everySiteStillYieldsOffersWithATitleALinkAndADate() throws IOException, InterruptedException {
        List<String> failures = new ArrayList<>();
        for (OfferReader reader : readers) {
            RecordingFetcher fetcher = new RecordingFetcher(http.fetcherFor(reader.domain()));
            FirstPage batch = new FirstPage();
            String failure;
            try {
                reader.read(fetcher, batch);
                failure = check(reader.site(), batch.offers);
            } catch (IOException | RuntimeException e) {
                failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            LOG.info("{}: {} offers on the first page, {} freelance{}", reader.site().getDisplayName(), batch.offers.size(),
                batch.offers.stream().filter(ScrapedOffer::isFreelance).count(), failure == null ? "" : " - " + failure);
            if (failure != null) {
                failures.add(reader.site().getDisplayName() + ": " + failure);
                fetcher.keep(SAMPLES.resolve(reader.site().name()));
            }
        }
        assertThat(failures).as("Sites whose reader no longer reads their offers").isEmpty();
    }

    /** What the first page lacks, or {@code null} when its freelance offers are whole. */
    static String check(OfferSite site, List<ScrapedOffer> offers) {
        List<ScrapedOffer> freelance = offers.stream().filter(ScrapedOffer::isFreelance).toList();
        if (freelance.isEmpty()) {
            return "no freelance offer on the first page (" + offers.size() + " read)";
        }
        List<String> missing = new ArrayList<>();
        if (freelance.stream().anyMatch(offer -> blank(offer.getTitle()))) {
            missing.add("title");
        }
        if (freelance.stream().anyMatch(offer -> blank(offer.getUrl()))) {
            missing.add("link");
        }
        if (!UNDATED.contains(site) && freelance.stream().allMatch(offer -> offer.getPublishedAt() == null)) {
            missing.add("date");
        }
        return missing.isEmpty() ? null : "offers without " + String.join(", ", missing);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Keeps the first page a reader hands on and stops it. */
    private static final class FirstPage implements OfferBatch {
        private final List<ScrapedOffer> offers = new ArrayList<>();

        @Override
        public boolean add(List<ScrapedOffer> page) {
            offers.addAll(page);
            return false;
        }

        @Override
        public Set<String> known(Collection<String> externalIds) {
            return Set.of();
        }
    }

    /** Fetches through the polite client and remembers what each address answered. */
    private static final class RecordingFetcher implements PageFetcher {
        private final PageFetcher fetcher;
        private final Map<String, String> answers = new LinkedHashMap<>();

        private RecordingFetcher(PageFetcher fetcher) {
            this.fetcher = fetcher;
        }

        @Override
        public String get(String url) throws IOException, InterruptedException {
            String body = fetcher.get(url);
            answers.put(url, body);
            return body;
        }

        @Override
        public String postJson(String url, String json) throws IOException, InterruptedException {
            String body = fetcher.postJson(url, json);
            answers.put(url, body);
            return body;
        }

        /** Writes each answer to {@code folder}, with the address it came from on its first line. */
        void keep(Path folder) throws IOException {
            Files.createDirectories(folder);
            int index = 0;
            for (Map.Entry<String, String> answer : answers.entrySet()) {
                Files.writeString(folder.resolve(String.format("%02d.txt", index++)), answer.getKey() + "\n" + answer.getValue(),
                    StandardCharsets.UTF_8);
            }
        }
    }
}
