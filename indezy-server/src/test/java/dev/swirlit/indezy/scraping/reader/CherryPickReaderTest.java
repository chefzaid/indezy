package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CherryPickReaderTest {

    private final CherryPickReader reader = new CherryPickReader();

    @Test
    void asksForFreelanceMissionsAndKeepsOnlyPublishedOnes() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource(CherryPickReader.SEARCH_URL, "cherry-pick-page.json");
        List<ScrapedOffer> offers = new ArrayList<>();

        reader.read(fetcher, page -> offers.addAll(page));

        assertThat(fetcher.postedBodies()).containsExactly("{\"page\":1,\"mission_type\":\"short\"}");
        assertThat(offers).extracting(ScrapedOffer::getExternalId).containsExactly("6789", "6790", "6792");
        // A salaried CDD job is not freelance, whatever its contract label.
        assertThat(offers).extracting(ScrapedOffer::isFreelance).containsExactly(true, false, true);
        ScrapedOffer net = offers.get(0);
        assertThat(net.getUrl()).isEqualTo("https://app.cherry-pick.io/ext/missions/developpeur-net-h-f-paris-6789");
        assertThat(net.getRateMin()).isEqualTo(550);
        assertThat(net.getRateMax()).isEqualTo(600);
        assertThat(net.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(net.getDurationMonths()).isEqualTo(12);
        assertThat(net.getSeniority()).isEqualTo(Seniority.SENIOR);
        assertThat(net.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(net.getRemoteDaysPerWeek()).isEqualTo(3);
        assertThat(net.getDescription()).isEqualTo("Contexte\n\nBack-office e-commerce");
        assertThat(net.getPublishedAt()).isEqualTo(Instant.parse("2026-10-14T10:12:12Z"));
        assertThat(net.getSkills()).containsExactly("C#", ".net core");
        ScrapedOffer cyber = offers.get(2);
        assertThat(cyber.getWorkMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(cyber.getSeniority()).isEqualTo(Seniority.JUNIOR);
    }

    @Test
    void readsEveryPageUpToTheLastOne() throws Exception {
        String page = "{\"data\":[{\"id\":1,\"name\":\"A\",\"slug\":\"a\",\"mission_type\":\"short\",\"status\":\"published\"}]," +
            "\"meta\":{\"last_page\":2}}";
        FakeFetcher fetcher = new FakeFetcher().page(CherryPickReader.SEARCH_URL, page);
        List<String> bodies = new ArrayList<>();

        reader.read(fetcher, offers -> bodies.add(offers.get(0).getExternalId()));

        assertThat(fetcher.postedBodies()).hasSize(2).last().asString().contains("\"page\":2");
        assertThat(bodies).hasSize(2);
        assertThat(reader.newestFirst()).isFalse();
        assertThat(reader.site()).isEqualTo(OfferSite.CHERRY_PICK);
        assertThat(reader.domain()).isEqualTo("cherry-pick.io");
    }

    @Test
    void stopsOnAnEmptyPage() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().page(CherryPickReader.SEARCH_URL, "{\"data\":[],\"meta\":{\"last_page\":9}}");

        reader.read(fetcher, offers -> true);

        assertThat(fetcher.postedBodies()).hasSize(1);
    }

    @Test
    void stopsWhenTheAnswerHasNoMissionsOrTheBatchHasEnough() throws Exception {
        FakeFetcher withoutList = new FakeFetcher().page(CherryPickReader.SEARCH_URL, "{\"message\":\"maintenance\"}");
        FakeFetcher endless = new FakeFetcher().page(CherryPickReader.SEARCH_URL,
            "{\"data\":[{\"id\":1,\"status\":\"published\"}],\"meta\":{}}");

        reader.read(withoutList, offers -> true);
        reader.read(endless, offers -> false);

        assertThat(withoutList.postedBodies()).hasSize(1);
        assertThat(endless.postedBodies()).hasSize(1);
    }

    @Test
    void readsEveryRemotePolicyAndLeavesOutRemovedMissions() throws Exception {
        assertThat(remote("full_remote")).isEqualTo(WorkMode.REMOTE);
        assertThat(remote("no_remote")).isEqualTo(WorkMode.ONSITE);
        assertThat(remote("on_site")).isEqualTo(WorkMode.ONSITE);
        assertThat(remote("occasional_remote")).isEqualTo(WorkMode.HYBRID);
        assertThat(remote("travel")).isNull();
        assertThat(remote(null)).isNull();
        assertThat(CherryPickReader.offer(Json.parse("{\"status\":\"draft\"}"))).isNull();
        assertThat(CherryPickReader.offer(Json.parse("{\"status\":\"published\",\"deleted_at\":\"2026-10-01T00:00:00\"}"))).isNull();
        ScrapedOffer bare = CherryPickReader.offer(Json.parse("{\"id\":3,\"status\":\"published\",\"created_at\":\"hier\"}"));
        assertThat(bare.getUrl()).isNull();
        assertThat(bare.getPublishedAt()).isNull();
    }

    private static WorkMode remote(String displacement) throws IOException {
        String mission = displacement == null ? "{\"status\":\"published\"}" : "{\"status\":\"published\",\"displacement\":\"" + displacement + "\"}";
        return CherryPickReader.offer(Json.parse(mission)).getWorkMode();
    }
}
