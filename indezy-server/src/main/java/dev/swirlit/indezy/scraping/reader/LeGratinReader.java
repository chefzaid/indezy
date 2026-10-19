package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFacts;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LeGratin (Nexoris's mission board): its missions page lists every open mission with its daily rate, context,
 * duration and place. Missions are identified by a MongoDB id, whose first four bytes are its creation time.
 */
@Component
public class LeGratinReader extends HtmlListReader {

    static final String LIST_URL = "https://www.legratin.io/missions";
    static final String OFFER_URL = "https://www.legratin.io/offer/";
    private static final Pattern MISSION = Pattern.compile("/offer/([0-9a-f]{24})\\b");

    @Override
    public OfferSite site() {
        return OfferSite.LEGRATIN;
    }

    @Override
    public String domain() {
        return "legratin.io";
    }

    @Override
    String pageUrl(int page) {
        return page == 1 ? LIST_URL : null;
    }

    @Override
    List<ScrapedOffer> offers(Document page) {
        List<ScrapedOffer> offers = new ArrayList<>();
        for (Element article : page.select("article:has(a[href*=/offer/])")) {
            Matcher mission = MISSION.matcher(article.selectFirst("a[href*=/offer/]").attr("href"));
            Element title = article.selectFirst("h2");
            if (!mission.find() || title == null) {
                continue;
            }
            String id = mission.group(1);
            Map<String, String> facts = PageFacts.of(article.select("dl > div"), "dt", "dd");
            // "Paris / 2 jours de télétravail / semaine": the city, then the remote policy.
            String[] place = facts.getOrDefault("localisation", "").split("/", 2);
            Element badge = article.selectFirst("span.rounded-full");
            Element summary = title.nextElementSibling();
            offers.add(ScrapedOffer.builder()
                .externalId(id)
                .url(OFFER_URL + id)
                .title(title.text())
                .description(HtmlText.join("\n\n", summary == null ? null : summary.text(), context(facts.get("contexte"))))
                // "Talent · 500€/j"
                .rateText(badge == null ? null : badge.text().substring(badge.text().indexOf('·') + 1).strip())
                .city(place[0].isBlank() ? null : place[0].strip())
                .remoteText(place.length > 1 ? place[1].strip() : null)
                .durationMonths(OfferText.duration(facts.get("duree")))
                .publishedAt(Instant.ofEpochSecond(Long.parseLong(id.substring(0, 8), 16)))
                .build());
        }
        return offers;
    }

    private static String context(String sector) {
        return sector == null ? null : "Contexte : " + sector;
    }
}
