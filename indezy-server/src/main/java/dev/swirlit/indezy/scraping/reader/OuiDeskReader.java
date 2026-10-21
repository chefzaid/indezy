package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OuiDesk: its sitemap lists its mission pages (with its talents' profiles, left aside), public without an account,
 * each describing itself as a "Mission freelance" and giving its place, start, duration, kind ("Régie") and
 * description. The pages give no date, so the missions are dated when first seen.
 */
@Component
public class OuiDeskReader extends DetailPagesReader {

    static final String SITEMAP = "https://ouidesk.com/sitemap.xml";
    /** "…/mission/Consultant-SAP-FSM-(H-F)-72080b29525e9418c8a3cca381141955/", the address encoded or not. */
    private static final Pattern MISSION = Pattern.compile("/mission/[^?#]*-([0-9a-f]{32})/?$");
    private static final String FREELANCE = "mission freelance";

    @Override
    public OfferSite site() {
        return OfferSite.OUIDESK;
    }

    @Override
    public String domain() {
        return "ouidesk.com";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        return Sitemaps.read(fetcher, SITEMAP, url -> externalId(url) != null).stream()
            .map(page -> new Entry(page.url(), externalId(page.url()), page.lastModified()))
            .toList();
    }

    @Override
    String externalId(String url) {
        Matcher mission = MISSION.matcher(url);
        return mission.find() ? mission.group(1) : null;
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        Element title = page.selectFirst("h4.text-blue");
        if (title == null) {
            return null;
        }
        Element summary = page.selectFirst("meta[name=description]");
        ScrapedOffer.ScrapedOfferBuilder offer = ScrapedOffer.builder()
            .title(title.text())
            // The site's own words for what each mission is.
            .freelance(summary != null && TextFolding.words(summary.attr("content")).startsWith(FREELANCE));
        place(offer, fact(page, "feather-map-pin"));
        String when = fact(page, "feather-calendar");
        if (when != null) {
            offer.startAsap(TextFolding.words(when).matches(".*\\b(des que possible|asap|immediat)\\b.*") ? Boolean.TRUE : null)
                .durationMonths(OfferText.duration(when));
        }
        Element project = page.selectFirst("h5:containsOwn(Projet)");
        Element body = project == null ? null : project.parent();
        if (body != null) {
            body.select("h5:containsOwn(Rémunération)").forEach(label -> {
                Element amount = label.nextElementSibling();
                if (amount != null && amount.text().matches(".*\\d.*")) {
                    offer.rateText(amount.text());
                }
                label.parent().remove();
            });
            offer.description(HtmlText.toText(body.html()));
        }
        return offer.build();
    }

    /** "Lille, France" is in Lille; "Full remote, France" is a remote mission. */
    private static void place(ScrapedOffer.ScrapedOfferBuilder offer, String place) {
        if (place == null) {
            return;
        }
        String city = place.split(",")[0].strip();
        if (OfferText.remote(city) != OfferText.Remote.UNKNOWN) {
            offer.remoteText(city);
        } else {
            offer.city(city);
        }
    }

    /** The text beside one of the header's icons, its lines kept apart. */
    private static String fact(Document page, String icon) {
        Element svg = page.selectFirst("svg." + icon);
        if (svg == null || svg.parent() == null) {
            return null;
        }
        String text = HtmlText.toText(svg.parent().html()).strip();
        return text.isEmpty() ? null : text;
    }
}
