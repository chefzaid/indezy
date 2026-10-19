package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.Countries;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferBatch;
import dev.swirlit.indezy.scraping.OfferReader;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Yeets's public Bubble Data API ({@code /api/1.1/obj/mission}), 100 missions a page, newest first.
 * The mission pages' addresses come from the site's sitemap, which ends each one with the mission id.
 */
@Component
public class YeetsReader implements OfferReader {

    static final String API = "https://www.yeets.fr/api/1.1/obj/mission?limit=100&sort_field=Created%20Date&descending=true&cursor=";
    static final String SITEMAP = "https://www.yeets.fr/sitemap-page-mission.xml";
    static final String OFFER_URL = "https://www.yeets.fr/page-mission/";
    private static final int PAGE_SIZE = 100;
    /** Missions still looking for a freelancer ("to start", "to confirm"). */
    private static final Set<String> OPEN_STATUSES = Set.of("a demarrer", "a valider");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Override
    public OfferSite site() {
        return OfferSite.YEETS;
    }

    @Override
    public String domain() {
        return "yeets.fr";
    }

    @Override
    public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        Map<String, String> pages = missionPages(fetcher);
        for (int cursor = 0; ; cursor += PAGE_SIZE) {
            JsonNode response = Json.parse(fetcher.get(API + cursor)).path("response");
            JsonNode missions = response.path("results");
            if (!missions.isArray() || missions.isEmpty() ||
                !batch.add(Json.each(missions, mission -> offer(mission, pages))) ||
                response.path("remaining").asInt(0) <= 0) {
                return;
            }
        }
    }

    /** Mission id to page address, from the sitemap; empty when the sitemap cannot be read. */
    static Map<String, String> missionPages(PageFetcher fetcher) throws InterruptedException {
        Map<String, String> pages = new HashMap<>();
        try {
            for (Element location : Jsoup.parse(fetcher.get(SITEMAP), "", Parser.xmlParser()).select("url > loc")) {
                String url = location.text().strip();
                int dash = url.lastIndexOf('-');
                if (url.startsWith(OFFER_URL) && dash > 0) {
                    pages.put(url.substring(dash + 1), url);
                }
            }
        } catch (IOException e) {
            // Without the sitemap, page addresses are rebuilt from the title (see slug).
            return pages;
        }
        return pages;
    }

    static ScrapedOffer offer(JsonNode mission, Map<String, String> pages) {
        String status = TextFolding.words(Json.text(mission.path("statutMissionOS")));
        if (status != null && !OPEN_STATUSES.contains(status)) {
            return null;
        }
        String id = Json.text(mission.path("_id"));
        String title = Json.text(mission.path("missionTitre"));
        JsonNode place = mission.path("missionLieu");
        String address = Json.text(place.path("address"));
        String[] parts = address == null ? new String[0] : address.split(",");
        Integer rate = Json.integer(mission.path("missionTJM"));
        OfferText.Remote remote = OfferText.remote(Json.text(mission.path("OsRemote")));
        return ScrapedOffer.builder()
            .externalId(id)
            .url(id == null ? null : pages.getOrDefault(id, OFFER_URL + slug(title) + "-" + id))
            .title(title)
            .rateMin(rate)
            .rateMax(rate)
            .city(parts.length > 0 ? parts[0].strip() : null)
            .countryCode(parts.length > 1 ? Countries.code(parts[parts.length - 1]) : null)
            .latitude(Json.decimal(place.path("lat")))
            .longitude(Json.decimal(place.path("lng")))
            .workMode(remote.mode())
            .remoteDaysPerWeek(remote.daysPerWeek())
            .durationMonths(Json.integer(mission.path("missionDuréeExacte")))
            .startDate(parisDate(Json.instant(mission.path("missionDateDémarrage"))))
            .seniority(OfferText.seniority(Json.text(mission.path("missionSenioritéOs"))))
            .skills(Json.each(mission.path("TechnoText"), Json::text))
            .publishedAt(Json.instant(mission.path("Created Date")))
            .build();
    }

    /** Bubble's page slug: the title in lower case with every other character a dash. */
    static String slug(String title) {
        return title == null ? "mission" : TextFolding.fold(title).replaceAll("[^a-z0-9]", "-");
    }

    /** Bubble stores a date as midnight Paris time in UTC ("2026-10-25T23:00:00Z" is 26 October). */
    private static LocalDate parisDate(Instant instant) {
        return instant == null ? null : instant.atZone(PARIS).toLocalDate();
    }
}
