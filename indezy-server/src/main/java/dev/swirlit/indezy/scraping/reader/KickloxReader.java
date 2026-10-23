package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Kicklox: its sitemap lists its published missions, freelance and salaried, and its mission pages render in the
 * browser from its own API on kicklox.io, which this reader asks for each mission instead (ADR 0009): its contract
 * ({@code missions}, shown as "Prestation", is the freelance one), daily rate, remote policy, place, start, duration
 * and texts. The site also lists the permanent and temporary jobs of its partners, and its own generic offers, standing
 * ads kept published to gather profiles for no client's mission in particular: both are set aside.
 */
@Component
public class KickloxReader extends OfferDetailsReader {

    static final String SITEMAP = "https://app.kicklox.com/sitemap-missions.xml";
    static final String API = "https://prod-kicklox.kicklox.io/offline_graphql";
    /** "…/missions/0012069c-e556-4b4e-9af3-2b2f2217a38b". */
    private static final Pattern MISSION = Pattern.compile("^https://app\\.kicklox\\.com/missions/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/?$");
    /** The query its own mission page sends, kept to what an offer needs. */
    private static final String QUERY = "query getMissionOffline($id: ID!) { mission(id: $id) { status job { name } context " +
        "context_description goals deliverables description skills { name } tools { name } contract_types modalities " +
        "location { city postal_code country_code } begin begin_at end_months daily_rate company { name } " +
        "company_confidentiality partner_offer partner_company_name generic_offer validated_at } }";
    private static final String FREELANCE = "missions";
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Pattern WORDS = Pattern.compile("\\p{L}|\\d");

    @Override
    public OfferSite site() {
        return OfferSite.KICKLOX;
    }

    @Override
    public String domain() {
        return "kicklox.com";
    }

    @Override
    public Optional<String> apiDomain() {
        return Optional.of("kicklox.io");
    }

    /**
     * The sitemap's missions. Most share one date, the day its partners' jobs (all salaried) were imported again rather
     * than when they changed: those keep no date, so the missions changed on their own day are read first.
     */
    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        List<Sitemaps.Entry> pages = Sitemaps.read(fetcher, SITEMAP, url -> externalId(url) != null);
        Map<Instant, Long> days = pages.stream().map(Sitemaps.Entry::lastModified).filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Instant imported = days.entrySet().stream().filter(day -> day.getValue() * 2 > pages.size())
            .map(Map.Entry::getKey).findFirst().orElse(null);
        return pages.stream()
            .map(page -> new Entry(page.url(), externalId(page.url()), Objects.equals(page.lastModified(), imported) ? null : page.lastModified()))
            .toList();
    }

    @Override
    String externalId(String url) {
        Matcher mission = MISSION.matcher(url);
        return mission.matches() ? mission.group(1) : null;
    }

    @Override
    ScrapedOffer details(PageFetcher fetcher, Entry entry) throws IOException, InterruptedException {
        // The identifier is the page's UUID, so it needs no escaping in the request.
        String request = "{\"operationName\":\"getMissionOffline\",\"variables\":{\"id\":\"" + entry.externalId() + "\"},\"query\":\"" + QUERY + "\"}";
        return offer(Json.parse(fetcher.postJson(API, request)).path("data").path("mission"));
    }

    /** The offer of a mission the API gave, or {@code null} when it is gone, no longer published or a generic offer. */
    static ScrapedOffer offer(JsonNode mission) {
        String title = Json.text(mission.path("job").path("name"));
        if (title == null || !"published".equals(Json.text(mission.path("status"))) || mission.path("generic_offer").asBoolean(false)) {
            return null;
        }
        ScrapedOffer.ScrapedOfferBuilder offer = ScrapedOffer.builder()
            .title(title)
            .company(company(mission))
            .description(HtmlText.join("\n\n", text(mission.path("context_description")), text(mission.path("goals")),
                text(mission.path("deliverables")), text(mission.path("description"))))
            .city(Json.text(mission.path("location").path("city")))
            .postalCode(Json.text(mission.path("location").path("postal_code")))
            .countryCode(Json.text(mission.path("location").path("country_code")))
            .durationMonths(Json.integer(mission.path("end_months")))
            .publishedAt(Json.instant(mission.path("validated_at")))
            // The site's own word for a freelance mission; its partners' permanent and temporary jobs say otherwise.
            .freelance(Json.each(mission.path("contract_types"), Json::text).contains(FREELANCE));
        JsonNode rate = mission.path("daily_rate");
        if (rate.isArray() && rate.size() == 2) {
            offer.rateMin(Json.integer(rate.get(0))).rateMax(Json.integer(rate.get(1)));
        }
        remote(offer, Json.text(mission.path("modalities")));
        String begin = Json.text(mission.path("begin"));
        Instant beginAt = Json.instant(mission.path("begin_at"));
        if ("asap".equals(begin)) {
            offer.startAsap(Boolean.TRUE);
        } else if ("precise".equals(begin) && beginAt != null) {
            offer.startDate(beginAt.atZone(PARIS).toLocalDate());
        }
        Set<String> skills = new LinkedHashSet<>(Json.each(mission.path("skills"), skill -> Json.text(skill.path("name"))));
        skills.addAll(Json.each(mission.path("tools"), tool -> Json.text(tool.path("name"))));
        return offer.skills(skills).build();
    }

    /** The publisher the page names: the partner who posted it, else the company, unless it keeps its name confidential. */
    private static String company(JsonNode mission) {
        String partner = Json.text(mission.path("partner_company_name"));
        if (partner != null) {
            return partner;
        }
        return mission.path("company_confidentiality").asBoolean(false) ? null : Json.text(mission.path("company").path("name"));
    }

    /** "remote" is full remote, "remote_can_move" on site with remote work possible, "on_site" on site. */
    private static void remote(ScrapedOffer.ScrapedOfferBuilder offer, String modalities) {
        if ("remote".equals(modalities)) {
            offer.workMode(WorkMode.REMOTE).remoteDaysPerWeek(5);
        } else if ("remote_can_move".equals(modalities)) {
            offer.workMode(WorkMode.HYBRID);
        } else if ("on_site".equals(modalities)) {
            offer.workMode(WorkMode.ONSITE).remoteDaysPerWeek(0);
        }
    }

    /** A text field's plain text; {@code null} for an empty one or a placeholder such as "-". */
    private static String text(JsonNode html) {
        String text = HtmlText.toText(Json.text(html));
        return text == null || !WORDS.matcher(text).find() ? null : text;
    }
}
