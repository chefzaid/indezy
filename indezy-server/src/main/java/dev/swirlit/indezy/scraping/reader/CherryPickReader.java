package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferBatch;
import dev.swirlit.indezy.scraping.OfferReader;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cherry Pick's mission search ({@code POST /api/mission/search}), 25 missions a page, asked for its
 * freelance missions only ({@code mission_type} "short", labelled "Freelance" on the site; "permanent"
 * covers its salaried CDI and CDD jobs). The contract type of a freelance mission is a hidden form
 * default, so it is ignored. Results are ranked by relevance, not date, so every run reads the page budget.
 */
@Component
public class CherryPickReader implements OfferReader {

    static final String SEARCH_URL = "https://app.cherry-pick.io/api/mission/search";
    static final String OFFER_URL = "https://app.cherry-pick.io/ext/missions/";
    /** Cherry Pick's mission type for freelance missions. */
    static final String FREELANCE = "short";
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** "partially_remote_3": three days of remote work a week. */
    private static final Pattern PARTIAL_REMOTE = Pattern.compile("partially_remote_(\\d)");

    @Override
    public OfferSite site() {
        return OfferSite.CHERRY_PICK;
    }

    @Override
    public String domain() {
        return "cherry-pick.io";
    }

    @Override
    public boolean newestFirst() {
        return false;
    }

    @Override
    public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        for (int page = 1; ; page++) {
            JsonNode result = Json.parse(fetcher.postJson(SEARCH_URL, "{\"page\":" + page + ",\"mission_type\":\"" + FREELANCE + "\"}"));
            JsonNode missions = result.path("data");
            if (!missions.isArray() || missions.isEmpty() || !batch.add(Json.each(missions, CherryPickReader::offer))) {
                return;
            }
            Integer lastPage = Json.integer(result.path("meta").path("last_page"));
            if (lastPage != null && page >= lastPage) {
                return;
            }
        }
    }

    static ScrapedOffer offer(JsonNode mission) {
        if (!"published".equals(Json.text(mission.path("status"))) || Json.text(mission.path("deleted_at")) != null) {
            return null;
        }
        String slug = Json.text(mission.path("slug"));
        OfferText.Remote remote = remote(Json.text(mission.path("displacement")));
        return ScrapedOffer.builder()
            .externalId(Json.text(mission.path("id")))
            .url(slug == null ? null : OFFER_URL + slug)
            .title(Json.text(mission.path("name")))
            .company(Json.text(mission.path("company").path("name")))
            .description(HtmlText.toText(Json.text(mission.path("description"))))
            .rateMin(Json.integer(mission.path("minimum_rate")))
            .rateMax(Json.integer(mission.path("maximum_rate")))
            .city(Json.text(mission.path("city")))
            .postalCode(Json.text(mission.path("postal_code")))
            .countryCode(Json.text(mission.path("country").path("id")))
            .workMode(remote.mode())
            .remoteDaysPerWeek(remote.daysPerWeek())
            .durationMonths(Json.integer(mission.path("duration")))
            .startDate(Json.date(mission.path("start_date")))
            .seniority(OfferText.seniorityOfYears(Json.integer(mission.path("minimum_years_experience"))))
            .skills(Json.each(mission.path("skills"), skill -> Json.text(skill.path("name"))))
            .publishedAt(parisTime(Json.text(mission.path("created_at"))))
            .freelance(FREELANCE.equals(Json.text(mission.path("mission_type"))))
            .build();
    }

    private static OfferText.Remote remote(String displacement) {
        if (displacement == null) {
            return OfferText.Remote.UNKNOWN;
        }
        Matcher partial = PARTIAL_REMOTE.matcher(displacement);
        if (partial.matches()) {
            return OfferText.remoteDaysPerWeek(Integer.parseInt(partial.group(1)));
        }
        if (displacement.contains("full_remote")) {
            return OfferText.remoteDaysPerWeek(5);
        }
        if (displacement.contains("no_remote") || displacement.contains("on_site")) {
            return OfferText.remoteDaysPerWeek(0);
        }
        return displacement.contains("remote") ? new OfferText.Remote(WorkMode.HYBRID, null) : OfferText.Remote.UNKNOWN;
    }

    /** Cherry Pick writes Paris local times without an offset ("2026-10-14T12:12:12"). */
    private static Instant parisTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value).atZone(PARIS).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
