package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FrenchDates;
import dev.swirlit.indezy.scraping.JobPostings;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * FreelanceRepublik: besides matching the profiles it holds, it publishes missions, listed newest first with their day
 * on its job board ({@code /freelance}) and each described on its own page ({@code /missions/<title>-<id>}) by a
 * schema.org {@code JobPosting} ({@code CONTRACTOR}, with the publication date, place and full description). The
 * platform names itself as the publisher. Its sitemap no longer lists the new missions since the board moved there from
 * {@code /missions} on 2026-11-10, and still lists removed ones.
 */
@Component
public class FreelanceRepublikReader extends DetailPagesReader {

    static final String JOB_BOARD = "https://www.freelancerepublik.com/freelance";
    private static final String MISSIONS = "/missions/";

    @Override
    public OfferSite site() {
        return OfferSite.FREELANCE_REPUBLIK;
    }

    @Override
    public String domain() {
        return "freelancerepublik.com";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        LocalDate today = LocalDate.now(FrenchDates.PARIS);
        List<Entry> entries = new ArrayList<>();
        for (Element card : Jsoup.parse(fetcher.get(JOB_BOARD), JOB_BOARD).select("a[href*=" + MISSIONS + "]")) {
            String url = card.absUrl("href");
            String id = externalId(url);
            if (id != null) {
                // "10 novembre 2026", the day the mission was posted.
                Element day = card.selectFirst("[data-jb=date]");
                entries.add(new Entry(url, id, day == null ? null : FrenchDates.startOfDay(FrenchDates.parse(day.text(), today))));
            }
        }
        return entries;
    }

    @Override
    String externalId(String url) {
        return after(url, MISSIONS);
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        return JobPostings.read(page).map(posting -> {
            ScrapedOffer offer = posting.build();
            String city = offer.getCity();
            // The locality repeats the region and the country ("Paris, Paris, France").
            return city == null || !city.contains(",") ? offer : offer.toBuilder().city(city.substring(0, city.indexOf(',')).strip()).build();
        }).orElse(null);
    }
}
