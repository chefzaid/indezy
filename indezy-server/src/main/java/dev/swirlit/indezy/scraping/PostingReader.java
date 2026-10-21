package dev.swirlit.indezy.scraping;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads the job posting of one page on any public site, for a link to a site the feed does not collect (see
 * docs/features.md#from-an-offer-link): through the collection's polite client (its {@code robots.txt}, its delay, its
 * size cap), kept to the page's own site and to the public internet ({@link PublicHosts}). The page's schema.org
 * {@code JobPosting} gives the most, as most job sites and applicant tracking systems publish one; without it, the
 * page's title and summary. Nothing read is stored.
 */
@Component
public class PostingReader {

    /** What a page gave. */
    public enum Outcome {
        /** A posting a freelancer can answer, or one that states no contract. */
        FOUND,
        /** A posting that states a permanent, fixed-term or other salaried contract. */
        NOT_FREELANCE,
        /** The page is gone or holds nothing to read. */
        NONE,
        /** The site could not be read: unreachable, an error, its {@code robots.txt}, or not on the public internet. */
        UNAVAILABLE
    }

    /** A page's outcome and, when found or salaried, its posting. */
    public record Reading(Outcome outcome, ScrapedOffer offer) {
    }

    private final SiteHttpClient http;
    private final PublicHosts.Resolver resolver;

    @Autowired
    public PostingReader(SiteHttpClient http) {
        this(http, InetAddress::getAllByName);
    }

    PostingReader(SiteHttpClient http, PublicHosts.Resolver resolver) {
        this.http = http;
        this.resolver = resolver;
    }

    /** The posting of the page at {@code uri}, an address {@code OfferLinkReader.parse} accepted. */
    public Reading read(URI uri) throws InterruptedException {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        Predicate<String> isPublic = name -> PublicHosts.isPublic(name, resolver);
        if (!isPublic.test(host)) {
            return new Reading(Outcome.UNAVAILABLE, null);
        }
        String html;
        try {
            // The page's site: its host, its apex too when the page is on www.
            html = http.fetcherFor(host.replaceFirst("^www\\.", ""), isPublic).get(uri.toString());
        } catch (SiteResponseException e) {
            return new Reading(e.getStatus() == 404 || e.getStatus() == 410 ? Outcome.NONE : Outcome.UNAVAILABLE, null);
        } catch (IOException e) {
            return new Reading(Outcome.UNAVAILABLE, null);
        }
        Document page = Jsoup.parse(html, uri.toString());
        ScrapedOffer offer = posting(page, uri.toString());
        if (offer == null) {
            return new Reading(Outcome.NONE, null);
        }
        List<String> employment = JobPostings.employmentTypes(page);
        boolean salaried = !employment.isEmpty() && employment.stream().noneMatch(type -> type.toUpperCase(Locale.ROOT).contains("CONTRACTOR")) ||
            OfferText.namesSalariedJob(offer.getTitle());
        return new Reading(salaried ? Outcome.NOT_FREELANCE : Outcome.FOUND, offer);
    }

    /** The page's posting, from its {@code JobPosting} else its title and summary; {@code null} without a title. */
    static ScrapedOffer posting(Document page, String url) {
        Optional<ScrapedOffer.ScrapedOfferBuilder> posting = JobPostings.read(page);
        ScrapedOffer.ScrapedOfferBuilder offer = posting.orElseGet(ScrapedOffer::builder);
        ScrapedOffer read = offer.build();
        String title = firstOf(read.getTitle(), meta(page, "og:title"), page.title(), text(page.selectFirst("h1")));
        if (title == null) {
            return null;
        }
        String description = firstOf(read.getDescription(), meta(page, "og:description"), meta(page, "description"));
        return offer.title(title).description(description).url(url).freelance(true).build();
    }

    private static String meta(Document page, String name) {
        Element meta = page.selectFirst("meta[property=" + name + "], meta[name=" + name + "]");
        return meta == null ? null : meta.attr("content");
    }

    private static String text(Element element) {
        return element == null ? null : element.text();
    }

    private static String firstOf(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }
}
