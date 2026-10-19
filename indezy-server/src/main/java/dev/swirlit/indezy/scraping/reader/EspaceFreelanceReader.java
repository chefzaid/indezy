package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FrenchDates;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Espace Freelance: its missions, newest first, nine a page, each card with its publication day, trade, agency,
 * reference, start and daily rate; the city closes the title ("Expert SOC _ Lille (H/F)").
 */
@Component
public class EspaceFreelanceReader extends HtmlListReader {

    static final String LIST_URL = "https://www.espace-freelance.fr/missions-disponibles/";
    /** "(H/F)", "(F/H)". */
    private static final Pattern GENDERS = Pattern.compile("\\(\\s*[HF]\\s*/\\s*[HF]\\s*\\)");

    @Override
    public OfferSite site() {
        return OfferSite.ESPACE_FREELANCE;
    }

    @Override
    public String domain() {
        return "espace-freelance.fr";
    }

    @Override
    String pageUrl(int page) {
        return page == 1 ? LIST_URL : LIST_URL + "page/" + page + "/";
    }

    @Override
    List<ScrapedOffer> offers(Document page) {
        List<ScrapedOffer> offers = new ArrayList<>();
        for (Element card : page.select(".uk-card:has(input.mission_id)")) {
            Element link = card.selectFirst("a[href*=/missions-disponibles/]");
            Element title = card.selectFirst("h2");
            if (link == null || title == null) {
                continue;
            }
            Element published = card.selectFirst(".uk-text-meta");
            LocalDate day = published == null ? null : FrenchDates.parse(published.text(), LocalDate.now(FrenchDates.PARIS));
            String start = fact(card, "date-demarrage");
            boolean asap = start != null && TextFolding.words(start).matches(".*\\b(asap|des que possible|immediat)\\b.*");
            offers.add(ScrapedOffer.builder()
                .externalId(card.selectFirst("input.mission_id").val())
                .url(link.absUrl("href"))
                .title(title.text())
                .city(city(title.text()))
                .description(HtmlText.join("\n", line("Métier", fact(card, "metier")), line("Agence", fact(card, "agence")),
                    line("Référence", fact(card, "reference"))))
                .rateText(fact(card, "tjm"))
                .startAsap(asap ? Boolean.TRUE : null)
                .startDate(asap || start == null || day == null ? null : FrenchDates.parse(start, day))
                .publishedAt(FrenchDates.startOfDay(day))
                .publishedDayOnly(true)
                .build());
        }
        return offers;
    }

    /** "Expert SOC _ Lille (H/F)" is in Lille, "Technicien Réseaux (F/H) – Lille" too. */
    static String city(String title) {
        String plain = GENDERS.matcher(title).replaceAll(" ");
        int underscore = plain.lastIndexOf(" _ ");
        int dash = plain.lastIndexOf(" – ");
        int cut = Math.max(underscore, dash);
        String city = cut < 0 ? "" : plain.substring(cut + 3).strip();
        return city.isEmpty() ? null : city;
    }

    private static String fact(Element card, String name) {
        Element value = card.selectFirst("li#mission-" + name + " strong");
        return value == null || value.text().isBlank() ? null : value.text().strip();
    }

    private static String line(String label, String value) {
        return value == null ? null : label + " : " + value;
    }
}
