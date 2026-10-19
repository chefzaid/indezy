package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.enums.WorkMode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads the schema.org {@code JobPosting} a page declares in JSON-LD: title, description, publisher, place, daily
 * rate, contract and dates. Only {@code CONTRACTOR} postings are open to freelancers.
 */
public final class JobPostings {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private JobPostings() {
    }

    /** The page's job posting as an offer to complete (identifier, link, site-specific facts), or empty. */
    public static Optional<ScrapedOffer.ScrapedOfferBuilder> read(Document page) {
        for (Element script : page.select("script[type=application/ld+json]")) {
            JsonNode posting = find(script.data());
            if (posting != null) {
                return Optional.of(offer(posting));
            }
        }
        return Optional.empty();
    }

    private static JsonNode find(String json) {
        JsonNode root;
        try {
            root = Json.parse(json);
        } catch (IOException e) {
            return null;
        }
        List<JsonNode> candidates = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(candidates::add);
        } else {
            candidates.add(root);
            root.path("@graph").forEach(candidates::add);
        }
        return candidates.stream().filter(node -> types(node).contains("JobPosting")).findFirst().orElse(null);
    }

    private static List<String> types(JsonNode node) {
        JsonNode type = node.path("@type");
        return type.isArray() ? Json.each(type, Json::text) : Optional.ofNullable(Json.text(type)).map(List::of).orElse(List.of());
    }

    static ScrapedOffer.ScrapedOfferBuilder offer(JsonNode posting) {
        String datePosted = Json.text(posting.path("datePosted"));
        boolean dayOnly = datePosted != null && datePosted.length() == 10;
        JsonNode address = first(posting.path("jobLocation")).path("address");
        JsonNode salary = posting.path("baseSalary");
        JsonNode amount = salary.path("value");
        boolean daily = "DAY".equalsIgnoreCase(Json.text(amount.path("unitText"))) || "DAY".equalsIgnoreCase(Json.text(salary.path("unitText")));
        Integer rate = daily ? Json.integer(amount.path("value")) : null;
        Integer min = daily ? Json.integer(amount.path("minValue")) : null;
        Integer max = daily ? Json.integer(amount.path("maxValue")) : null;
        if (daily && rate == null && amount.isNumber()) {
            rate = Json.integer(amount);
        }
        List<String> employment = values(posting.path("employmentType"));
        return ScrapedOffer.builder()
            .title(HtmlText.unescape(Json.text(posting.path("title"))))
            .description(HtmlText.toText(Json.text(posting.path("description"))))
            .company(HtmlText.unescape(Json.text(posting.path("hiringOrganization").path("name"))))
            .city(HtmlText.unescape(Json.text(address.path("addressLocality"))))
            .postalCode(Json.text(address.path("postalCode")))
            .regionName(Json.text(address.path("addressRegion")))
            .countryCode(country(address.path("addressCountry")))
            .rateMin(rate != null ? rate : min)
            .rateMax(rate != null ? rate : max)
            .workMode("TELECOMMUTE".equalsIgnoreCase(Json.text(posting.path("jobLocationType"))) ? WorkMode.REMOTE : null)
            .skills(values(posting.path("skills")))
            .publishedAt(dayOnly ? day(datePosted) : Json.instant(posting.path("datePosted")))
            .publishedDayOnly(dayOnly)
            .expiresAt(Json.instant(posting.path("validThrough")))
            .freelance(employment.stream().anyMatch(type -> type.toUpperCase(Locale.ROOT).contains("CONTRACTOR")));
    }

    /** A JSON array or a comma-separated text as a list. */
    private static List<String> values(JsonNode node) {
        if (node.isArray()) {
            return Json.each(node, Json::text);
        }
        String text = Json.text(node);
        return text == null ? List.of() : List.of(text.split("\\s*,\\s*"));
    }

    private static JsonNode first(JsonNode node) {
        return node.isArray() ? node.path(0) : node;
    }

    /** "FR", or a country object or name. */
    private static String country(JsonNode node) {
        String value = node.isObject() ? Json.text(node.path("name")) : Json.text(node);
        if (value == null) {
            return null;
        }
        return value.length() == 2 ? value.toUpperCase(Locale.ROOT) : Countries.code(value);
    }

    private static Instant day(String value) {
        try {
            return LocalDate.parse(value).atStartOfDay(PARIS).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
