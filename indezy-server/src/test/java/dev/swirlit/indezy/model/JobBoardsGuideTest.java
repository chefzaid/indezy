package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The help center's offers article names every board the feed reads, with its site, and every board it leaves out, in both languages. */
class JobBoardsGuideTest {

    @Test
    void theOffersArticleListsEveryBoard() throws IOException {
        for (String language : new String[] {"en", "fr"}) {
            Path article = Path.of("..", "indezy-web", "src", "assets", "help", language, "offers.md");
            // Built without the web app beside it, there is no article to check.
            assumeTrue(Files.exists(article));
            String text = Files.readString(article, StandardCharsets.UTF_8);
            for (OfferSite site : OfferSite.values()) {
                assertThat(text).as("%s in the %s article", site, language)
                    .contains("| " + site.getDisplayName() + " | " + site.getHomeUrl().replace("https://", "") + " |");
            }
            for (OfferLeftOutSite site : OfferLeftOutSite.values()) {
                assertThat(text).as("%s in the %s article", site, language).contains(site.getDisplayName());
            }
        }
    }
}
