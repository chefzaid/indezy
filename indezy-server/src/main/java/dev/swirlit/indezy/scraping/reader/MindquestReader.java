package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FrenchDates;
import dev.swirlit.indezy.scraping.JobPostings;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mindquest: its missions sitemap lists the IT and finance missions, each page a schema.org {@code JobPosting}
 * whose employment types include {@code CONTRACTOR} for freelance missions (CDI vacancies are left out), with the
 * start date, rate and topic tags in the page header.
 */
@Component
public class MindquestReader extends DetailPagesReader {

    static final String SITEMAP = "https://mindquest.io/fr/sitemap-missions.xml";
    /** ".../missions-freelance-offres-emploi-it-finance/94436/architecte-d-entreprise-hf-59". */
    private static final Pattern MISSION = Pattern.compile("/fr/missions-freelance-offres-emploi-it-finance/(\\d+)/");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final String STARTS = "a partir de";

    @Override
    public OfferSite site() {
        return OfferSite.MINDQUEST;
    }

    @Override
    public String domain() {
        return "mindquest.io";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        List<Entry> entries = new ArrayList<>();
        // The sitemap also lists the search pages; only mission pages carry an identifier.
        for (Sitemaps.Entry page : Sitemaps.read(fetcher, SITEMAP, url -> true)) {
            Matcher mission = MISSION.matcher(page.url());
            if (mission.find()) {
                entries.add(new Entry(page.url(), mission.group(1), page.lastModified()));
            }
        }
        return entries;
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        return JobPostings.read(page).map(posting -> {
            ScrapedOffer offer = posting.build();
            ScrapedOffer.ScrapedOfferBuilder completed = offer.toBuilder();
            List<String> tags = new ArrayList<>(offer.getSkills());
            for (Element tag : page.select("span[title^=#]")) {
                String name = tag.attr("title").substring(1).strip();
                // The header shows the mission number the same way ("# 94436").
                if (!name.matches("\\d+")) {
                    tags.add(name);
                }
            }
            completed.clearSkills().skills(tags);
            Element start = page.selectFirst("span:matchesOwn((?i)^\\s*(à|a) partir d)");
            if (start != null) {
                LocalDate reference = offer.getPublishedAt() == null ? LocalDate.now(PARIS) : LocalDate.ofInstant(offer.getPublishedAt(), PARIS);
                completed.startDate(FrenchDates.parse(TextFolding.fold(start.text()).replace(STARTS, ""), reference));
                Element rate = start.parent() == null ? null : start.parent().nextElementSibling();
                if (rate != null && rate.text().matches(".*\\d.*") && offer.getRateMin() == null && offer.getRateMax() == null) {
                    completed.rateText(rate.text());
                }
            }
            return completed.build();
        }).orElse(null);
    }
}
