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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LeHibou's mission search ({@code POST api.lehibou.com/api/search/mission/list}). It answers with the
 * ten newest missions whatever the paging asked for, so the site is read often (see {@link OfferSite}).
 * Pro bono missions are left out: they are unpaid.
 */
@Component
public class LeHibouReader implements OfferReader {

    static final String SEARCH_URL = "https://api.lehibou.com/api/search/mission/list";
    static final String OFFER_URL = "https://www.lehibou.com/annonce/";
    /** "2d": two days of remote work a week. */
    private static final Pattern REMOTE_DAYS = Pattern.compile("(\\d)d");

    @Override
    public OfferSite site() {
        return OfferSite.LEHIBOU;
    }

    @Override
    public String domain() {
        return "lehibou.com";
    }

    @Override
    public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        JsonNode missions = Json.parse(fetcher.postJson(SEARCH_URL, "{}")).path("missions");
        batch.add(Json.each(missions, LeHibouReader::offer));
    }

    static ScrapedOffer offer(JsonNode mission) {
        if (mission.path("isProBono").asBoolean(false)) {
            return null;
        }
        String id = Json.text(mission.path("id"));
        JsonNode city = mission.path("city");
        Integer rate = Json.integer(mission.path("tjm"));
        OfferText.Remote remote = remote(Json.text(mission.path("remoteWork")));
        return ScrapedOffer.builder()
            .externalId(id)
            .url(id == null ? null : OFFER_URL + id)
            .title(Json.text(mission.path("title")))
            .description(HtmlText.toText(Json.text(mission.path("description"))))
            .rateMin(rate)
            .rateMax(rate)
            .city(Json.text(city.path("title")))
            .postalCode(Json.text(city.path("postalcode")))
            .departmentName(Json.text(city.path("department").path("title")))
            .regionName(Json.text(city.path("region").path("title")))
            .countryCode(Json.text(mission.path("country").path("code")))
            .latitude(Json.decimal(city.path("geolocation").path("lat")))
            .longitude(Json.decimal(city.path("geolocation").path("lon")))
            .workMode(remote.mode())
            .remoteDaysPerWeek(remote.daysPerWeek())
            .durationMonths(OfferText.durationMonths(Json.decimal(mission.path("duration")), Json.text(mission.path("durationUnit"))))
            .startDate(Json.date(mission.path("start")))
            .skills(Json.each(mission.path("skills"), skill -> Json.text(skill.path("title"))))
            .publishedAt(Json.instant(mission.path("createdAt")))
            .build();
    }

    private static OfferText.Remote remote(String remoteWork) {
        if (remoteWork == null) {
            return OfferText.Remote.UNKNOWN;
        }
        Matcher days = REMOTE_DAYS.matcher(remoteWork);
        if (days.matches()) {
            return OfferText.remoteDaysPerWeek(Integer.parseInt(days.group(1)));
        }
        return switch (remoteWork) {
            case "full", "remote", "fullRemote" -> OfferText.remoteDaysPerWeek(5);
            case "none", "onsite", "0" -> OfferText.remoteDaysPerWeek(0);
            case "partial", "hybrid" -> new OfferText.Remote(WorkMode.HYBRID, null);
            default -> OfferText.Remote.UNKNOWN;
        };
    }
}
