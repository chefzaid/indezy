package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFacts;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sixième Homme: only the missions its home page showcases are public, each on its own page with its start,
 * duration, average daily rate, place, remote policy and description. The pages give no date: missions are dated
 * when first seen.
 */
@Component
public class SixiemeHommeReader extends DetailPagesReader {

    static final String HOME_URL = "https://sixiemehomme.io/";
    private static final String MISSION = "freelance.sixiemehomme.io/mission/";

    @Override
    public OfferSite site() {
        return OfferSite.SIXIEME_HOMME;
    }

    @Override
    public String domain() {
        return "sixiemehomme.io";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        List<Entry> entries = new ArrayList<>();
        for (Element link : Jsoup.parse(fetcher.get(HOME_URL), HOME_URL).select("a[href*=" + MISSION + "]")) {
            String url = link.absUrl("href");
            entries.add(new Entry(url, url.substring(url.lastIndexOf('/') + 1), null));
        }
        return entries;
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        Element title = page.selectFirst(".mission-details-header h1");
        if (title == null) {
            return null;
        }
        Map<String, String> facts = PageFacts.of(page.select(".mission-row"), ".row-label", ".row-value");
        String start = facts.get("debut");
        Element sector = page.selectFirst(".mission-details-header .sub-text");
        Element description = page.selectFirst(".mission-description");
        // The remote policy is the one row without a label ("Télétravail partiel").
        Element remote = page.selectFirst(".mission-row:not(:has(.row-label)) .row-value");
        return ScrapedOffer.builder()
            .title(title.text())
            .description(HtmlText.toText((sector == null ? "" : sector.outerHtml()) + (description == null ? "" : description.html())))
            .skills(page.select(".mission-row .list-labels span").eachText())
            .startAsap(start != null && TextFolding.words(start).matches(".*\\b(immediat|asap|des que possible)\\b.*") ? Boolean.TRUE : null)
            .durationMonths(OfferText.duration(facts.get("duree")))
            .rateText(facts.get("tarif journalier moyen"))
            .city(facts.get("localisation"))
            .remoteText(remote == null ? null : remote.text())
            .build();
    }
}
