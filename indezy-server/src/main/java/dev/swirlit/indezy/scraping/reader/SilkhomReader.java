package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FrenchDates;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Silkhom (also upway.io, which redirects to it): one page lists every offer, the freelance ones marked with the
 * {@code contrat-Freelance} class, with their city, publication day and description; CDI cards are left out.
 */
@Component
public class SilkhomReader extends HtmlListReader {

    static final String LIST_URL = "https://www.silkhom.com/nos-offres-demploi/";

    @Override
    public OfferSite site() {
        return OfferSite.SILKHOM;
    }

    @Override
    public String domain() {
        return "silkhom.com";
    }

    @Override
    String pageUrl(int page) {
        return page == 1 ? LIST_URL : null;
    }

    @Override
    List<ScrapedOffer> offers(Document page) {
        List<ScrapedOffer> offers = new ArrayList<>();
        for (Element card : page.select("div.wrapper-offre.contrat-Freelance")) {
            Element link = card.selectFirst("h2 a[href]");
            if (link == null) {
                continue;
            }
            ScrapedOffer.ScrapedOfferBuilder offer = ScrapedOffer.builder()
                .externalId(card.attr("post-id"))
                .url(link.absUrl("href"))
                .title(link.text());
            // Each fact is told by its icon: a map marker for the city, a calendar for the publication day.
            for (Element fact : card.select(".infos-lieu-date p:has(img)")) {
                String icon = fact.selectFirst("img").attr("src");
                if (icon.contains("map-marker")) {
                    offer.city(fact.text());
                } else if (icon.contains("calendar")) {
                    offer.publishedAt(FrenchDates.startOfDay(FrenchDates.parse(fact.text(), LocalDate.now(FrenchDates.PARIS))))
                        .publishedDayOnly(true);
                }
            }
            Element description = card.selectFirst(".conteneur-description");
            offers.add(offer.description(description == null ? null : HtmlText.toText(description.html())).build());
        }
        return offers;
    }
}
