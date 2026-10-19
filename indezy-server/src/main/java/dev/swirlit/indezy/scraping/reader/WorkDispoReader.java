package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FrenchDates;
import dev.swirlit.indezy.scraping.JobPostings;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFacts;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WorkDispo (formerly freelance-dispo.fr): its sitemap lists every job with its last change, and each job page carries
 * a schema.org {@code JobPosting} plus a facts panel (remote days, start, experience, contract). Only
 * {@code CONTRACTOR} postings whose contract reads Freelance are kept; the site also publishes CDI and CDD jobs.
 */
@Component
public class WorkDispoReader extends DetailPagesReader {

    static final String SITEMAP = "https://www.workdispo.com/sitemap.xml";
    private static final String JOB_PATH = "/job/";
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})");

    @Override
    public OfferSite site() {
        return OfferSite.WORKDISPO;
    }

    @Override
    public String domain() {
        return "workdispo.com";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        return Sitemaps.read(fetcher, SITEMAP, url -> url.contains(JOB_PATH)).stream()
            .map(page -> new Entry(page.url(), page.url().substring(page.url().indexOf(JOB_PATH) + JOB_PATH.length()), page.lastModified()))
            .toList();
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        return JobPostings.read(page).map(posting -> complete(posting, facts(page))).orElse(null);
    }

    private static ScrapedOffer complete(ScrapedOffer.ScrapedOfferBuilder posting, Map<String, String> facts) {
        ScrapedOffer offer = posting.build();
        ScrapedOffer.ScrapedOfferBuilder completed = offer.toBuilder();
        String contract = facts.get("contrat");
        if (contract != null) {
            completed.freelance(offer.isFreelance() && TextFolding.fold(contract).contains("freelance"));
        }
        if (offer.getCity() == null) {
            completed.city(facts.get("localisation"));
        }
        String remote = facts.get("teletravail");
        if (remote != null) {
            String folded = TextFolding.words(remote);
            if (folded.matches("non|aucun|0|pas de teletravail")) {
                completed.workMode(WorkMode.ONSITE);
            } else {
                completed.remoteText("Télétravail " + remote);
            }
        }
        String start = facts.get("demarrage");
        if (start != null) {
            if (TextFolding.words(start).contains("des que possible") || TextFolding.words(start).contains("asap")) {
                completed.startAsap(true);
            } else {
                LocalDate reference = offer.getPublishedAt() == null ? LocalDate.now(PARIS) : LocalDate.ofInstant(offer.getPublishedAt(), PARIS);
                completed.startDate(FrenchDates.parse(start, reference));
            }
        }
        String experience = facts.get("experience");
        if (experience != null) {
            Matcher years = YEARS.matcher(experience);
            completed.seniority(years.find() ? OfferText.seniorityOfYears(Integer.valueOf(years.group(1))) : OfferText.seniority(experience));
        }
        return completed.build();
    }

    /** The facts panel ("Télétravail" to "2 jours min."), keyed by the folded label. */
    static Map<String, String> facts(Document page) {
        return PageFacts.of(page.select("dl.wdof-tuiles .wdof-tuile"), "dt", "dd");
    }
}
