package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FakePublishers;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Free-Work's public JSON API ({@code /api/job_postings}), filtered on contractor offers, 50 a page,
 * newest first. Offers of publishers known for fake listings are dropped.
 */
@Component
public class FreeWorkReader implements OfferReader {

    static final String API = "https://www.free-work.com/api/job_postings?contracts=contractor&itemsPerPage=50&page=";
    static final String OFFER_URL = "https://www.free-work.com/fr/tech-it/job-mission/";

    @Override
    public OfferSite site() {
        return OfferSite.FREE_WORK;
    }

    @Override
    public String domain() {
        return "free-work.com";
    }

    @Override
    public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        for (int page = 1; ; page++) {
            JsonNode postings = Json.parse(fetcher.get(API + page));
            if (!postings.isArray() || postings.isEmpty()) {
                return;
            }
            if (!batch.add(Json.each(postings, FreeWorkReader::offer))) {
                return;
            }
        }
    }

    static ScrapedOffer offer(JsonNode posting) {
        String company = Json.text(posting.path("company").path("name"));
        if (FakePublishers.named(company)) {
            return null;
        }
        String slug = Json.text(posting.path("slug"));
        String jobSlug = Json.text(posting.path("job").path("slug"));
        JsonNode location = posting.path("location");
        Set<String> contracts = Set.copyOf(Json.each(posting.path("contracts"), Json::text));
        String description = Stream.of(HtmlText.toText(Json.text(posting.path("description"))),
                HtmlText.toText(Json.text(posting.path("candidateProfile"))))
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n\n"));
        OfferText.Remote remote = remote(Json.text(posting.path("remoteMode")));
        return ScrapedOffer.builder()
            .externalId(Json.text(posting.path("id")))
            .url(slug == null ? null : OFFER_URL + (jobSlug == null ? "autre" : jobSlug) + "/" + slug)
            .title(Json.text(posting.path("title")))
            .company(company)
            .description(description.isEmpty() ? null : description)
            .rateMin(Json.integer(posting.path("minDailySalary")))
            .rateMax(Json.integer(posting.path("maxDailySalary")))
            .city(Json.text(location.path("locality")))
            .postalCode(Json.text(location.path("postalCode")))
            .departmentName(Json.text(location.path("adminLevel2")))
            .regionName(Json.text(location.path("adminLevel1")))
            .countryCode(Json.text(location.path("countryCode")))
            .latitude(Json.decimal(location.path("latitude")))
            .longitude(Json.decimal(location.path("longitude")))
            .workMode(remote.mode())
            .remoteDaysPerWeek(remote.daysPerWeek())
            .durationMonths(OfferText.durationMonths(Json.decimal(posting.path("durationValue")), Json.text(posting.path("durationPeriod"))))
            .startDate(Json.date(posting.path("startsAt")))
            .seniority(OfferText.seniority(Json.text(posting.path("experienceLevel"))))
            .skills(Json.each(posting.path("skills"), skill -> Json.text(skill.path("name"))))
            .publishedAt(Json.instant(posting.path("publishedAt")))
            .expiresAt(Json.instant(posting.path("expiredAt")))
            .freelance(contracts.contains("contractor"))
            .build();
    }

    private static OfferText.Remote remote(String mode) {
        if (mode == null) {
            return OfferText.Remote.UNKNOWN;
        }
        return switch (mode) {
            case "none" -> OfferText.remoteDaysPerWeek(0);
            case "full" -> OfferText.remoteDaysPerWeek(5);
            case "partial" -> new OfferText.Remote(WorkMode.HYBRID, null);
            default -> OfferText.Remote.UNKNOWN;
        };
    }
}
