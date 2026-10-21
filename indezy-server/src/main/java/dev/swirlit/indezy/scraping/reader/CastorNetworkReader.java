package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FrenchDates;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Castor Network, Alten's freelance network: its missions, newest first, five a page, each card with its publication
 * day ("Publiée le 12/10/2026"), headline, role and description, then facts marked by their icon: the client, duration
 * ("+ de 6 mois"), start, place and experience. Engineering (industry, aerospace, nuclear) as well as IT; every mission
 * is a freelance one.
 */
@Component
public class CastorNetworkReader extends HtmlListReader {

    static final String LIST_URL = "https://www.castor-network.com/fr/mission-freelance/?page=";
    /** "/fr/mission-freelance/3571/developpement-de-a-a-z-…". */
    private static final Pattern MISSION = Pattern.compile("/fr/mission-freelance/(\\d+)/");
    private static final int DATE = 0;
    private static final int HEADLINE = 1;
    private static final int ROLE = 2;

    @Override
    public OfferSite site() {
        return OfferSite.CASTOR_NETWORK;
    }

    @Override
    public String domain() {
        return "castor-network.com";
    }

    @Override
    String pageUrl(int page) {
        return LIST_URL + page;
    }

    @Override
    List<ScrapedOffer> offers(Document page) {
        List<ScrapedOffer> offers = new ArrayList<>();
        for (Element card : page.select(".missionListItem")) {
            Element link = card.selectFirst("a[href*=/fr/mission-freelance/]");
            Matcher id = link == null ? null : MISSION.matcher(link.attr("href"));
            Elements lines = card.select(".missionContainer-missionDescription > p");
            if (id == null || !id.find() || lines.size() <= ROLE) {
                continue;
            }
            LocalDate day = FrenchDates.parse(lines.get(DATE).text(), LocalDate.now(FrenchDates.PARIS));
            String headline = lines.get(HEADLINE).text().strip();
            String role = lines.get(ROLE).text().strip();
            Element description = card.selectFirst(".line-clamp");
            ScrapedOffer.ScrapedOfferBuilder offer = ScrapedOffer.builder()
                .externalId(id.group(1))
                .url(link.absUrl("href"))
                .title(role.isEmpty() ? headline : role)
                .description(HtmlText.join("\n\n", headline, description == null ? null : HtmlText.toText(description.html())))
                .publishedAt(FrenchDates.startOfDay(day))
                .publishedDayOnly(true);
            for (Element info : card.select(".missionContainer-missionInfo")) {
                Element icon = info.selectFirst("i");
                Element value = info.selectFirst("p");
                if (icon != null && value != null && !value.text().isBlank()) {
                    fact(offer, icon.className(), value.text().strip(), day);
                }
            }
            offers.add(offer.build());
        }
        return offers;
    }

    /** One of a card's facts, named by its icon. */
    private static void fact(ScrapedOffer.ScrapedOfferBuilder offer, String icon, String value, LocalDate published) {
        if (icon.contains("icon-etps")) {
            offer.company(value);
        } else if (icon.contains("icon-attente")) {
            offer.durationMonths(OfferText.duration(value));
        } else if (icon.contains("icon-date")) {
            boolean asap = TextFolding.words(value).matches(".*\\b(asap|des que possible|immediat)\\b.*");
            offer.startAsap(asap ? Boolean.TRUE : null)
                .startDate(asap || published == null ? null : FrenchDates.parse(value, published));
        } else if (icon.contains("icon-map_pointer")) {
            offer.city(value);
        } else if (icon.contains("icon-xp")) {
            offer.seniority(OfferText.seniority(value));
        }
    }
}
