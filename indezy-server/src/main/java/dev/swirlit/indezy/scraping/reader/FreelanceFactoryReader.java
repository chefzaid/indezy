package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Freelance Factory: its WordPress REST API lists the missions ({@code poste}) with their publication time, and each
 * mission page shows its key facts as an icon list (place, daily rate, duration) and its description in a tab. Every
 * mission is a freelance assignment.
 */
@Component
public class FreelanceFactoryReader extends DetailPagesReader {

    static final String API = "https://freelance-factory.fr/wp-json/wp/v2/poste?per_page=100&orderby=date&order=desc" +
        "&_fields=id,date_gmt,modified_gmt,link,title";

    @Override
    public OfferSite site() {
        return OfferSite.FREELANCE_FACTORY;
    }

    @Override
    public String domain() {
        return "freelance-factory.fr";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        List<Entry> entries = new ArrayList<>();
        for (JsonNode mission : Json.parse(fetcher.get(API))) {
            String link = Json.text(mission.path("link"));
            String id = Json.text(mission.path("id"));
            if (link != null && id != null) {
                String title = HtmlText.unescape(Json.text(mission.path("title").path("rendered")));
                entries.add(new Entry(link, id, WordPress.utc(Json.text(mission.path("modified_gmt"))), title,
                    WordPress.utc(Json.text(mission.path("date_gmt")))));
            }
        }
        return entries;
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        ScrapedOffer.ScrapedOfferBuilder offer = ScrapedOffer.builder()
            .title(entry.title())
            .publishedAt(entry.publishedAt());
        for (Element item : page.select("li.elementor-icon-list-item")) {
            Element icon = item.selectFirst("svg");
            Element text = item.selectFirst(".elementor-icon-list-text");
            if (icon == null || text == null) {
                continue;
            }
            String value = text.text().strip();
            String kind = icon.className();
            if (kind.contains("map-marker")) {
                offer.departmentName(value).regionName(value);
            } else if (kind.contains("euro")) {
                offer.rateText(value + " €");
            } else if (kind.contains("calendar")) {
                offer.durationMonths(OfferText.durationInText(value, true));
            }
        }
        Element description = page.selectFirst("div[role=tabpanel][data-tab-index=2]");
        offer.description(description == null ? null : HtmlText.toText(description.html()));
        return offer.build();
    }
}
