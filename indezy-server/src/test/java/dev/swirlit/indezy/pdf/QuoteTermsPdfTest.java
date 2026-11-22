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

    @Test
    void drawsTheTitleInTheSellersAccentColour() throws IOException {
        InvoiceSeller seller = seller();
        seller.setAccentColor("#1F4E79");
        byte[] pdf = renderer.render(quote(), seller, new InvoiceBuyer(), 30, null);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String operators = new String(document.getPage(0).getContents().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            // 0x1F/255, 0x4E/255 and 0x79/255 set as the fill colour.
            assertThat(operators).containsPattern("0\\.12\\d* 0\\.30\\d* 0\\.47\\d* rg");
        }
    }

    @Test
    void printsTheSellersLogoAboveItsName() throws IOException {
        java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(300, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(picture, "png", png);
        dev.swirlit.indezy.model.DocumentLogo logo = new dev.swirlit.indezy.model.DocumentLogo();
        logo.setContent(png.toByteArray());
        dev.swirlit.indezy.repository.DocumentLogoRepository logos = org.mockito.Mockito.mock(dev.swirlit.indezy.repository.DocumentLogoRepository.class);
        org.mockito.Mockito.when(logos.findById(5L)).thenReturn(java.util.Optional.of(logo));
        QuotePdfRenderer withLogos = new QuotePdfRenderer();
        withLogos.setLogos(logos);
        InvoiceSeller seller = seller();
        seller.setLogoId(5L);
        try (PDDocument document = Loader.loadPDF(withLogos.render(quote(), seller, new InvoiceBuyer(), 30, null))) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).hasSize(1);
        }
        // Without one, or with one gone, nothing is drawn.
        seller.setLogoId(6L);
        org.mockito.Mockito.when(logos.findById(6L)).thenReturn(java.util.Optional.empty());
        try (PDDocument document = Loader.loadPDF(withLogos.render(quote(), seller, new InvoiceBuyer(), 30, null))) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isEmpty();
        }
    }

    @Test
    void writesTheFooterAndThePageNumbersAtTheFootOfEveryPage() throws IOException {
        InvoiceSeller seller = seller();
        seller.setFooter("alice.dev · 06 12 34 56 78");
        String terms = "Article 1. ".repeat(1200);
        try (PDDocument document = Loader.loadPDF(renderer.render(quote(), seller, new InvoiceBuyer(), 30, terms))) {
            int pages = document.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(document)).contains("alice.dev · 06 12 34 56 78", page + "/" + pages);
            }
        }
        // One page without a footer: nothing at its foot.
        try (PDDocument document = Loader.loadPDF(renderer.render(quote(), seller(), new InvoiceBuyer(), 30, null))) {
            assertThat(new PDFTextStripper().getText(document)).doesNotContain("1/1");
        }
    }
}

