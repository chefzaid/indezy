package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.Json;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * KatchMe: its WordPress REST API lists the offers ({@code offre}) newest first, their contract in their classes
 * ({@code type-contrat-freelance}, {@code type-contrat-cdi}) and whether they are filled ({@code disponibilite-pourvu}),
 * so only its open freelance offers are read. Each offer's page gives, after its title, its place, daily rate
 * ("TJM 400-550€"), remote policy and contract, then its description.
 */
@Component
public class KatchMeReader extends DetailPagesReader {

    /** The hundred newest offers, about four months of them: longer than the feed keeps. */
    static final String API = "https://www.katchme.fr/wp-json/wp/v2/offre?per_page=100&_fields=date_gmt,modified_gmt,link,title,class_list";
    private static final String OFFERS = "/offre/";
    private static final String FREELANCE = "type-contrat-freelance";
    private static final String FILLED = "disponibilite-pourvu";
    /** The contract terms of an offer's page, folded. */
    private static final Set<String> CONTRACTS = Set.of("freelance", "cdi", "cdd", "stage", "alternance");

    @Override
    public OfferSite site() {
        return OfferSite.KATCHME;
    }

    @Override
    public String domain() {
        return "katchme.fr";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        List<Entry> entries = new ArrayList<>();
        for (JsonNode offer : Json.parse(fetcher.get(API))) {
            List<String> classes = Json.each(offer.path("class_list"), Json::text);
            String link = Json.text(offer.path("link"));
            String id = link == null ? null : externalId(link);
            if (id != null && classes.contains(FREELANCE) && !classes.contains(FILLED)) {
                entries.add(new Entry(link, id, WordPress.utc(Json.text(offer.path("modified_gmt"))),
                    HtmlText.unescape(Json.text(offer.path("title").path("rendered"))), WordPress.utc(Json.text(offer.path("date_gmt")))));
            }
        }
        return entries;
    }

    @Override
    String externalId(String url) {
        return after(url, OFFERS);
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        Element title = page.selectFirst("#topbtn .elementor-heading-title");
        if (title == null) {
            return null;
        }
        ScrapedOffer.ScrapedOfferBuilder offer = ScrapedOffer.builder()
            .title(entry.title() != null ? entry.title() : title.text())
            .publishedAt(entry.publishedAt())
            .freelance(false);
        // The page lists other offers before this one; its own facts follow its title, its contract last.
        boolean own = false;
        String city = null;
        for (Element element : page.select("#topbtn, .elementor-post-info__terms-list-item")) {
            String value = element.text().strip();
            String folded = TextFolding.words(value);
            if ("topbtn".equals(element.id())) {
                own = true;
            } else if (!own || folded == null || folded.isEmpty()) {
                continue;
            } else if (CONTRACTS.contains(folded)) {
                offer.freelance("freelance".equals(folded));
                break;
            } else if (folded.startsWith("tjm")) {
                offer.rateText(value);
            } else if (OfferText.remote(value) != OfferText.Remote.UNKNOWN) {
                offer.remoteText(value);
            } else if (city == null) {
                city = value;
            }
        }
        Element description = page.selectFirst("#blocA");
        return offer.city(city).description(description == null ? null : HtmlText.toText(description.html())).build();
    }
}
