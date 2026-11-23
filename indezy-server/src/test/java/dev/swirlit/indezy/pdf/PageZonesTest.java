package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The footer's three zones on every page and the header of the following pages, placeholders filled, in the PDF and the Word copy. */
class PageZonesTest {

    private static Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setLanguage(DocumentLanguage.EN);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setNumber("F2026-0042");
        invoice.setIssueDate(LocalDate.of(2026, 11, 2));
        invoice.setDueDate(LocalDate.of(2026, 12, 2));
        invoice.setLines(Collections.nCopies(60, new InvoiceLine("Development", BigDecimal.ONE, new BigDecimal("600.00"), new BigDecimal("20.00"))));
        return invoice;
    }

    private static InvoiceSeller seller() {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        seller.setSiret("55210055400012");
        seller.setFooter("alice.dev");
        seller.setFooterCenter("SIRET {{seller.siret}}");
        seller.setFooterRight("Page {{page}} of {{pages}}");
        seller.setPageHeaderLeft("{{seller.name}}");
        seller.setPageHeaderRight("{{document.title}} {{document.number}}");
        return seller;
    }

    @Test
    void writesTheZonesOnEveryPageAndTheHeaderFromTheSecond() throws IOException {
        try (PDDocument document = Loader.loadPDF(new InvoicePdfRenderer().render(invoice(), seller(), new InvoiceBuyer()))) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            int pages = document.getNumberOfPages();
            String first = page(document, 1);
            assertThat(first).contains("alice.dev", "SIRET 55210055400012", "Page 1 of " + pages).doesNotContain("Invoice F2026-0042 ")
                .doesNotContain("1/" + pages);
            assertThat(page(document, 2)).contains("Alice Martin", "Invoice F2026-0042", "Page 2 of " + pages);
        }
    }

    @Test
    void keepsThePageNumberOnTheRightWithoutARightZone() throws IOException {
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Alice Martin");
        try (PDDocument document = Loader.loadPDF(new InvoicePdfRenderer().render(invoice(), seller, new InvoiceBuyer()))) {
            assertThat(page(document, 1)).contains("1/" + document.getNumberOfPages());
        }
    }

    @Test
    void writesTheZonesInTheWordCopyWithItsPageFields() throws IOException {
        Map<String, byte[]> parts = BillingDocxTest.parts(new InvoicePdfRenderer().renderDocx(invoice(), seller(), new InvoiceBuyer()));
        String footer = new String(parts.get("word/footer1.xml"), StandardCharsets.UTF_8);
        assertThat(footer).contains("alice.dev", "SIRET 55210055400012", "Page ", "w:instr=\"PAGE\"", "w:instr=\"NUMPAGES\"");
        assertThat(new String(parts.get("word/header1.xml"), StandardCharsets.UTF_8)).contains("Alice Martin", "Invoice F2026-0042");
        assertThat(new String(parts.get("word/document.xml"), StandardCharsets.UTF_8)).contains("<w:titlePg/>", "w:type=\"first\"");
        assertThat(new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8)).contains("/word/header1.xml");

        Map<String, byte[]> plain = BillingDocxTest.parts(new InvoicePdfRenderer().renderDocx(invoice(), new InvoiceSeller(), new InvoiceBuyer()));
        assertThat(plain).doesNotContainKey("word/header1.xml");
        assertThat(new String(plain.get("word/footer1.xml"), StandardCharsets.UTF_8)).contains("w:instr=\"PAGE\"");
    }

    private static String page(PDDocument document, int number) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(number);
        stripper.setEndPage(number);
        return stripper.getText(document).replaceAll("\\s+", " ");
    }

    @Test
    void showsTheLogoInAZoneThatAsksForIt() throws IOException {
        java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(300, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(picture, "png", png);
        dev.swirlit.indezy.model.DocumentLogo logo = new dev.swirlit.indezy.model.DocumentLogo();
        logo.setContent(png.toByteArray());
        dev.swirlit.indezy.repository.DocumentLogoRepository logos = org.mockito.Mockito.mock(dev.swirlit.indezy.repository.DocumentLogoRepository.class);
        org.mockito.Mockito.when(logos.findById(5L)).thenReturn(java.util.Optional.of(logo));
        InvoicePdfRenderer renderer = new InvoicePdfRenderer();
        renderer.setLogos(logos);
        InvoiceSeller seller = seller();
        seller.setLogoId(5L);
        seller.setFooter("{{logo}} alice.dev");
        try (PDDocument document = Loader.loadPDF(renderer.render(invoice(), seller, new InvoiceBuyer()))) {
            // The logo above the seller on the first page, and small in the footer of every page.
            assertThat(document.getPage(0).getResources().getXObjectNames()).hasSize(2);
            assertThat(document.getPage(1).getResources().getXObjectNames()).hasSize(1);
            assertThat(page(document, 1)).contains("alice.dev").doesNotContain("{{logo}}");
        }
        Map<String, byte[]> parts = BillingDocxTest.parts(renderer.renderDocx(invoice(), seller, new InvoiceBuyer()));
        assertThat(new String(parts.get("word/footer1.xml"), StandardCharsets.UTF_8)).contains("r:embed=\"rIdZoneLogo\"", "alice.dev")
            .doesNotContain("{{logo}}");
        assertThat(new String(parts.get("word/_rels/footer1.xml.rels"), StandardCharsets.UTF_8)).contains("media/logo.png");

        // Without a logo, the token is left out.
        InvoiceSeller bare = seller();
        bare.setFooter("{{logo}} alice.dev");
        try (PDDocument document = Loader.loadPDF(new InvoicePdfRenderer().render(invoice(), bare, new InvoiceBuyer()))) {
            assertThat(page(document, 1)).contains("alice.dev").doesNotContain("{{logo}}");
        }
        assertThat(new String(BillingDocxTest.parts(new InvoicePdfRenderer().renderDocx(invoice(), bare, new InvoiceBuyer())).get("word/footer1.xml"),
            StandardCharsets.UTF_8)).doesNotContain("{{logo}}", "rIdZoneLogo");
    }
}
