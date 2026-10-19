package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferBatch;
import dev.swirlit.indezy.scraping.OfferReader;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collective.work's job pages ({@code /jobs/fr?page=}), 30 offers each, newest first. The offers are in
 * the page's Next.js data ({@code __NEXT_DATA__}); only those whose contract types include FREELANCE are kept.
 */
@Component
public class CollectiveReader implements OfferReader {

    static final String LIST_URL = "https://www.collective.work/jobs/fr?page=";
    static final String OFFER_URL = "https://www.collective.work/jobs/fr/";
    /** "92400 Courbevoie, France" or "Paris, France". */
    private static final Pattern LOCATION = Pattern.compile("^(?:(\\d{5})\\s+)?(.+?)(?:,\\s*([^,]+))?$");

    @Override
    public OfferSite site() {
        return OfferSite.COLLECTIVE;
    }

    @Override
    public String domain() {
        return "collective.work";
    }

    @Override
    public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        for (int page = 1; ; page++) {
            JsonNode projects = projects(fetcher.get(LIST_URL + page));
            if (projects.isEmpty() || !batch.add(Json.each(projects, CollectiveReader::offer))) {
                return;
            }
        }
    }

    /** The {@code projects} array of the search results in a job page, empty when the page has none. */
    static JsonNode projects(String html) throws IOException {
        Element data = Jsoup.parse(html).getElementById("__NEXT_DATA__");
        if (data == null) {
            throw new IOException("Collective.work page without __NEXT_DATA__");
        }
        for (JsonNode query : Json.parse(data.data()).path("props").path("pageProps").path("dehydratedState").path("queries")) {
            JsonNode projects = query.path("state").path("data").path("results").path("projects");
            if (projects.isArray()) {
                return projects;
            }
        }
        throw new IOException("Collective.work page without search results");
    }

    static ScrapedOffer offer(JsonNode project) {
        String slug = Json.text(project.path("slug"));
        // Only offers marked freelance: CDI and CDD jobs, and offers stating no contract, stay out.
        boolean freelance = Json.each(project.path("contractTypes"), Json::text).contains("FREELANCE");
        String description = HtmlText.toText(Json.text(project.path("description")));
        String location = Json.text(project.path("location").path("fullNameFrench"));
        Matcher place = location == null ? null : LOCATION.matcher(location);
        boolean located = place != null && place.matches();
        List<String> skills = new ArrayList<>(Json.each(project.path("projectTypeSuggestions"), Json::text));
        skills.addAll(Json.each(project.path("projectTypes"), type -> label(Json.text(type))));
        return ScrapedOffer.builder()
            .externalId(Json.text(project.path("id")))
            .url(slug == null ? null : OFFER_URL + slug)
            .title(Json.text(project.path("name")))
            .company(Json.text(project.path("company").path("name")))
            .description(description != null ? description : Json.text(project.path("sumUp")))
            .rateText(Json.text(project.path("budgetBrief")))
            .postalCode(located ? place.group(1) : null)
            .city(located ? place.group(2) : null)
            .countryCode(located ? Countries.code(place.group(3)) : null)
            .workMode(workMode(Json.each(project.path("workPreferences"), Json::text)))
            .skills(skills)
            .publishedAt(Json.instant(project.path("publishedAt")))
            .expiresAt(Json.instant(project.path("expirationDate")))
            .freelance(freelance)
            .build();
    }

    /** Full remote when remote work is accepted at all, hybrid, then on site. */
    private static WorkMode workMode(List<String> preferences) {
        if (preferences.contains("REMOTE")) {
            return WorkMode.REMOTE;
        }
        if (preferences.contains("HYBRID")) {
            return WorkMode.HYBRID;
        }
        return preferences.contains("ON_SITE") ? WorkMode.ONSITE : null;
    }

    /** "SQL_QUERIES" reads "Sql queries", "JAVA" "Java"; three-letter codes ("CRM", "AWS") stay upper case. */
    static String label(String code) {
        if (code == null) {
            return null;
        }
        if (code.length() <= 3) {
            return code;
        }
        String words = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
