package dev.swirlit.indezy.pdf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The help center's Document templates article names every placeholder the filler knows, in both languages. */
class PlaceholderGuideTest {

    @Test
    void theGuideListsEveryPlaceholder() throws IOException {
        for (String language : new String[] {"en", "fr"}) {
            Path article = Path.of("..", "indezy-web", "src", "assets", "help", language, "document-templates.md");
            // Built without the web app beside it, there is no article to check.
            assumeTrue(Files.exists(article));
            String text = Files.readString(article, StandardCharsets.UTF_8);
            for (String placeholder : DocxTemplate.PLACEHOLDERS) {
                assertThat(text).as("%s in the %s guide", placeholder, language).contains("`{{" + placeholder + "}}`");
            }
        }
    }
}
