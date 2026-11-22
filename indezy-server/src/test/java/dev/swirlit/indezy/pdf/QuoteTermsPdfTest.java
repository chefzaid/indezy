package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteTermsPdfTest {

    private final QuotePdfRenderer renderer = new QuotePdfRenderer();

    private static Quote quote() {
        Quote quote = new Quote();
        quote.setProjectRole("Lead Java");
        return quote;
    }

    private static InvoiceSeller seller() {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        return seller;
    }

    @Test
    void printsTheTermsAndConditionsOnTheirOwnPagesAfterTheQuote() throws IOException {
        String terms = "Article 1 — Objet\nLes présentes conditions s'appliquent à toute prestation.\n\n" + "Article 2. ".repeat(1200);
        try (PDDocument document = Loader.loadPDF(renderer.render(quote(), seller(), new InvoiceBuyer(), 30, terms))) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(3);
            PDFTextStripper first = new PDFTextStripper();
            first.setEndPage(1);
            assertThat(first.getText(document)).contains("Les conditions générales de vente jointes s'appliquent.")
                .doesNotContain("Article 1");
            PDFTextStripper rest = new PDFTextStripper();
            rest.setStartPage(2);
            assertThat(rest.getText(document)).contains("Conditions générales de vente", "Article 1 — Objet", "toute prestation");
        }
    }

    @Test
    void addsNothingWithoutTerms() throws IOException {
        try (PDDocument document = Loader.loadPDF(renderer.render(quote(), seller(), new InvoiceBuyer(), 30, "  "))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).doesNotContain("conditions générales");
        }
    }
}
