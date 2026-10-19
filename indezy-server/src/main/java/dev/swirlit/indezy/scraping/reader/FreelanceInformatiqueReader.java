package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FrenchDates;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.util.FrenchGeography;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Freelance-Informatique: its freelance offers, 50 a page, newest first, each with its skills, place, start and
 * duration. The offer's reference starts with its publication day ({@code 261019C005} is 19 October 2026). Some
 * titles link through a Base64 {@code data-obf} attribute instead of an {@code href}.
 */
@Component
public class FreelanceInformatiqueReader extends HtmlListReader {

    static final String SITE_URL = "https://www.freelance-informatique.fr";
    static final String LIST_URL = SITE_URL + "/offres-freelance?page=";
    private static final Pattern REFERENCE = Pattern.compile("-((\\d{2})(\\d{2})(\\d{2})[A-Z]\\d{3})$");
    /** "93 - Saint Denis". */
    private static final Pattern PLACE = Pattern.compile("^(\\d{2,3}|2[AB])\\s*-\\s*(.+)$");

    @Override
    public OfferSite site() {
        return OfferSite.FREELANCE_INFORMATIQUE;
    }

    @Override
    public String domain() {
        return "freelance-informatique.fr";
    }

    @Override
    String pageUrl(int page) {
        return LIST_URL + page;
    }

    @Override
    List<ScrapedOffer> offers(Document page) {
        List<ScrapedOffer> offers = new ArrayList<>();
        for (Element card : page.select("div.job-card-line")) {
            Element title = card.selectFirst("h2.job-title");
            String path = title == null ? null : path(title);
            Matcher reference = path == null ? null : REFERENCE.matcher(path);
            if (reference == null || !reference.find()) {
                continue;
            }
            ScrapedOffer.ScrapedOfferBuilder offer = ScrapedOffer.builder()
                .externalId(reference.group(1))
                .url(SITE_URL + path)
                .title(title.text())
                .publishedAt(FrenchDates.startOfDay(day(reference)))
                .publishedDayOnly(true)
                .skills(card.select(".tags span").eachText());
            Element excerpt = card.selectFirst("p.line-clamp-2");
            offer.description(excerpt == null ? null : excerpt.text());
            for (Element fact : card.select("li:has(i.icon)")) {
                facts(offer, fact.selectFirst("i.icon").className(), fact.text());
            }
            offers.add(offer.build());
        }
        return offers;
    }

    private static void facts(ScrapedOffer.ScrapedOfferBuilder offer, String icon, String value) {
        if (icon.contains("icon-map")) {
            Matcher place = PLACE.matcher(value);
            if (place.matches()) {
                offer.departmentName(FrenchGeography.department(place.group(1)).map(FrenchGeography.Department::name).orElse(null))
                    .city(place.group(2));
            } else if (TextFolding.words(value).matches(".*\\b(teletravail|remote|distance)\\b.*")) {
                offer.remoteText(value);
            } else {
                offer.city(value);
            }
        } else if (icon.contains("icon-calendar")) {
            if (TextFolding.words(value).contains("asap")) {
                offer.startAsap(true);
            } else {
                offer.startDate(FrenchDates.parse(value, LocalDate.now(FrenchDates.PARIS)));
            }
        } else if (icon.contains("icon-time")) {
            offer.durationMonths(OfferText.duration(value));
        }
    }

    /** The offer's path on the site: its link, or the Base64 path of an obfuscated title; {@code null} otherwise. */
    private static String path(Element title) {
        Element link = title.selectFirst("a[href]");
        Element obfuscated = title.selectFirst("[data-obf]");
        String path = null;
        if (link != null) {
            path = link.attr("href");
        } else if (obfuscated != null) {
            try {
                path = new String(Base64.getDecoder().decode(obfuscated.attr("data-obf")), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return path != null && path.startsWith("/") ? path : null;
    }

    private static LocalDate day(Matcher reference) {
        try {
            return LocalDate.of(2000 + Integer.parseInt(reference.group(2)), Integer.parseInt(reference.group(3)),
                Integer.parseInt(reference.group(4)));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
