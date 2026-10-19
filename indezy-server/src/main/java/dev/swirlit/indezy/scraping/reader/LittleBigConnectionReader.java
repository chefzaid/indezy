package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.Countries;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferBatch;
import dev.swirlit.indezy.scraping.OfferReader;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.SiteResponseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

/**
 * LittleBig Connection: each of its nine mission categories ({@code /fr/rfp/<category>}) carries its open missions
 * in the page's React Server Components payload ({@code self.__next_f.push}). The missions are open to independent
 * consultants and give a daily budget, but no publication date: they are dated when first seen.
 */
@Component
public class LittleBigConnectionReader implements OfferReader {

    static final String CATEGORY_URL = "https://www.littlebigconnection.com/fr/rfp/";
    static final List<String> CATEGORIES = List.of("software-engineering", "cloud-engineering", "data-science-ai",
        "cybersecurity", "network-Infra", "digital-transformation", "ux-ui-design", "marketing-seo", "csr-esg");
    private static final String CHUNK_START = "self.__next_f.push([1,\"";
    private static final String MISSIONS = "\"rfps\":";

    @Override
    public OfferSite site() {
        return OfferSite.LITTLE_BIG_CONNECTION;
    }

    @Override
    public String domain() {
        return "littlebigconnection.com";
    }

    @Override
    public boolean newestFirst() {
        return false;
    }

    @Override
    public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        for (String category : CATEGORIES) {
            String page;
            try {
                page = fetcher.get(CATEGORY_URL + category);
            } catch (SiteResponseException e) {
                // A category without open missions answers 404 (four of nine on 2026-10-19).
                if (e.getStatus() == 404) {
                    continue;
                }
                throw e;
            }
            if (!batch.add(Json.each(missions(page), LittleBigConnectionReader::offer))) {
                return;
            }
        }
    }

    /** The {@code rfps} array of a category page, empty when the category has no open mission. */
    static JsonNode missions(String html) throws IOException {
        StringBuilder payload = new StringBuilder();
        for (int start = html.indexOf(CHUNK_START); start >= 0; start = html.indexOf(CHUNK_START, start + 1)) {
            int end = closingQuote(html, start + CHUNK_START.length());
            if (end < 0) {
                break;
            }
            payload.append(Json.parse(html.substring(start + CHUNK_START.length() - 1, end + 1)).asString());
        }
        int start = payload.indexOf(MISSIONS);
        if (start < 0) {
            throw new IOException("LittleBig Connection page without missions");
        }
        return Json.parseFirst(payload.substring(start + MISSIONS.length()));
    }

    /** Where the JSON string literal starting at {@code from} ends, escapes skipped; -1 when it never does. */
    private static int closingQuote(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    static ScrapedOffer offer(JsonNode mission) {
        String url = Json.text(mission.path("url"));
        String location = Json.text(mission.path("location"));
        int comma = location == null ? -1 : location.lastIndexOf(',');
        JsonNode budget = mission.path("budget");
        boolean daily = budget.isObject() && "daily".equalsIgnoreCase(Json.text(budget.path("unit")));
        return ScrapedOffer.builder()
            .externalId(url == null ? null : url.substring(url.lastIndexOf('/') + 1))
            .url(url)
            .title(Json.text(mission.path("title")))
            .description(Json.text(mission.path("description")))
            .city(comma < 0 ? location : location.substring(0, comma).strip())
            .countryCode(comma < 0 ? null : Countries.code(location.substring(comma + 1)))
            .remoteText(Json.text(mission.path("executionModality")))
            .startDate(Json.date(mission.path("projectStartDate")))
            .skills(Json.each(mission.path("skills"), Json::text))
            .rateMin(daily ? Json.integer(budget.path("min")) : null)
            .rateMax(daily ? Json.integer(budget.path("max")) : null)
            .build();
    }
}
